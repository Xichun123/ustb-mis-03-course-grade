package com.ustb.coursegrade.common;

import java.util.List;

/**
 * 统一分页响应结构：{items, total, page, page_size}
 */
public class PageResult<T> {

    private final List<T> items;
    private final long total;
    private final int page;
    private final int page_size;

    public PageResult(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.page_size = pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getPage_size() {
        return page_size;
    }
}
