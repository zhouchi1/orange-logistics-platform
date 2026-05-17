package com.orange.logistics.waybill.dto;

import lombok.Data;

@Data
public class SignConfirmDTO {
    private Long waybillId;
    private String waybillNo;
    private String signType; // PHOTO, SIGNATURE, PROXY
    private String signPhotoUrl;
    private String signatureData; // Base64 encoded signature
    private String signedBy;
    private String remark;
}
