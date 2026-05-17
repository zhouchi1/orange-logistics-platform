package com.orange.logistics.order.enums;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    ONLINE(1, "在线支付"),
    COD(2, "货到付款"),
    MONTHLY(3, "月结");

    private final int code;
    private final String desc;

    PaymentMethod(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
