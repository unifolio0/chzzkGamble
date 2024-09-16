package com.chzzkGamble.advertise.dto;

import com.chzzkGamble.advertise.domain.Advertise;
import lombok.Getter;

@Getter
public class AdvertiseRequest {

    private String name;
    private String imageUrl;
    private Long cost;

    public Advertise toEntity() {
        return new Advertise(name, imageUrl, cost);
    }
}