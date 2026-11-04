package com.techasians.alertservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> {
    private List<T> list;
    private int page;
    private int size;
    private long total;
    private int totalPages;

    public PageResult() {
    }

    public PageResult(List<T> list, int page, int size, long total, int totalPages) {
        this.list = list;
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / (double) size) : 0;
        return new PageResult<>(list, page, size, total, totalPages);
    }

    public List<T> getList() {
        return list;
    }

    public PageResult<T> setList(List<T> list) {
        this.list = list;
        return this;
    }

    public int getPage() {
        return page;
    }

    public PageResult<T> setPage(int page) {
        this.page = page;
        return this;
    }

    public int getSize() {
        return size;
    }

    public PageResult<T> setSize(int size) {
        this.size = size;
        return this;
    }

    public long getTotal() {
        return total;
    }

    public PageResult<T> setTotal(long total) {
        this.total = total;
        return this;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public PageResult<T> setTotalPages(int totalPages) {
        this.totalPages = totalPages;
        return this;
    }
}
