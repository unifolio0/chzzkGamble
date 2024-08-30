package com.chzzkGamble.gamble.roulette.dto;

import com.chzzkGamble.gamble.roulette.domain.RouletteElement;
import lombok.Getter;

@Getter
public class RouletteElementResponse {

    Long id;
    String name;
    Integer count;

    public RouletteElementResponse(Long id, String name, Integer count) {
        this.id = id;
        this.name = name;
        this.count = count;
    }

    public static RouletteElementResponse from(RouletteElement element) {
        return new RouletteElementResponse(
                element.getId(),
                element.getName(),
                element.getCount()
        );
    }
}