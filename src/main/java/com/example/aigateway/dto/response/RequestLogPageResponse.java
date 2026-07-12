package com.example.aigateway.dto.response;

import java.util.List;

/** Serialized response data for request log page operations. */
public class RequestLogPageResponse {
    private List<RequestLogResponse> items;
    private int page;
    private int size;
    private long total;
    private int totalPages;

    public List<RequestLogResponse> getItems() {
        return items;
    }

    public void setItems(List<RequestLogResponse> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
