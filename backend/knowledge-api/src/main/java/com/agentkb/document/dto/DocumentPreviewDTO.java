package com.agentkb.document.dto;

/** 原文档预览数据。 */
public class DocumentPreviewDTO {

    private byte[] data;
    private String contentType;
    private String fileName;

    public DocumentPreviewDTO() {
    }

    public DocumentPreviewDTO(byte[] data, String contentType, String fileName) {
        this.data = data;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
