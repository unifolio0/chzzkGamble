package com.chzzkGamble.chzzk.chat.service;

import com.chzzkGamble.chzzk.api.ChzzkApiService;
import com.chzzkGamble.chzzk.chat.domain.Chat;
import com.chzzkGamble.chzzk.chat.repository.ChatRepository;
import com.chzzkGamble.chzzk.dto.DonationMessage;
import com.chzzkGamble.event.AbnormalWebSocketClosedEvent;
import com.chzzkGamble.event.DonationEvent;
import com.chzzkGamble.exception.ChzzkException;
import com.chzzkGamble.exception.ChzzkExceptionCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChzzkChatService {

    private static final int MAX_CONNECTION_LIMIT = 10;
    private static final int CHAT_ALIVE_MINUTES = 10;

    private final ChzzkApiService apiService;
    private final ChatRepository chatRepository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final Map<String, ChzzkWebSocketClient> chatClients = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastEventPublished = new ConcurrentHashMap<>();

    @Transactional
    public void connectChatRoom(String channelName) {
        if (isChatAlreadyOpen(channelName)) {
            handleAlreadyOpenChat(channelName);
            return;
        }

        if (isConnectLimitExceeded()) {
            throw new ChzzkException(ChzzkExceptionCode.CHAT_CONNECTION_LIMIT);
        }

        connectWebSocket(channelName);

        createAndSaveNewChat(channelName);
    }

    private boolean isChatAlreadyOpen(String channelName) {
        return chatRepository.existsByChannelNameAndOpenedIsTrue(channelName);
    }

    private void handleAlreadyOpenChat(String channelName) {
        if (chatClients.containsKey(channelName)) {
            // 이미 열려있는 채팅방으로 연결.
            return;
        }

        // TODO 이 부분은 서버가 여러 대일 때, sticky fail로 인해 발생합니다.
        // TODO 각 서버가 현재 어느 채팅방과 연결 중인지 Redis 등을 통해 확인 후 해당 서버로 요청을 보내야 합니다.
        // TODO 이를 제대로 만들기 위해서는 kafka와 같은 message queue를 만들어야 할 것으로 예상됨.
        throw new ChzzkException(ChzzkExceptionCode.CHAT_CONNECTION_ERROR, "channelName :" + channelName);
    }

    private boolean isConnectLimitExceeded() {
        return chatClients.size() > MAX_CONNECTION_LIMIT;
    }

    private void createAndSaveNewChat(String channelName) {
        Chat chat = new Chat(channelName);
        chat.open();
        chatRepository.save(chat);
    }

    private void connectWebSocket(String channelName) {
        ChzzkWebSocketClient socketClient = new ChzzkWebSocketClient(apiService, publisher, channelName);
        socketClient.connect();
        chatClients.put(channelName, socketClient);
        lastEventPublished.put(channelName, LocalDateTime.now(clock));
    }

    @EventListener(AbnormalWebSocketClosedEvent.class)
    void reconnectChatRoom(AbnormalWebSocketClosedEvent event) {
        String channelName = (String) event.getSource();
        try {
            chatClients.get(channelName).connect();
        } catch (ChzzkException e) {
            chatClients.remove(channelName);
            throw new ChzzkException(ChzzkExceptionCode.CHAT_RECONNECTION_ERROR);
        }
    }

    @EventListener(DonationEvent.class)
    void updateLastEventTime(DonationEvent donationEvent) {
        DonationMessage donationMessage = (DonationMessage) donationEvent.getSource();
        String channelName = donationMessage.getChannelName();

        lastEventPublished.put(channelName, LocalDateTime.now(clock));
    }

    private void disconnectChatRoom(String channelName) {
        if (!chatClients.containsKey(channelName)) {
            throw new ChzzkException(ChzzkExceptionCode.CHAT_IS_DISCONNECTED, "channelName : " + channelName);
        }
        ChzzkWebSocketClient socketClient = chatClients.remove(channelName);
        socketClient.disconnect();

        chatRepository.findByChannelNameAndOpenedIsTrue(channelName)
                .ifPresent(Chat::close);
        log.info("connection with {} is closed by timeout", channelName);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${chat.close-interval}")
    public void disconnectChatRoom() {
        List<String> inactiveChannels = getInactiveChannels();

        inactiveChannels.forEach(channel -> {
            disconnectChatRoom(channel);
            lastEventPublished.remove(channel);
        });
    }

    private List<String> getInactiveChannels() {
        return lastEventPublished.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(LocalDateTime.now(clock).minusMinutes(CHAT_ALIVE_MINUTES)))
                .map(Map.Entry::getKey)
                .toList();
    }

    public boolean isConnected(String channelName) {
        return chatClients.containsKey(channelName) && chatClients.get(channelName).isConnected();
    }
}
