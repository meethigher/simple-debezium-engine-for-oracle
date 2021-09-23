package com.example.demo;

import java.io.Serializable;
import java.util.Map;

/**
 * @author chenchuancheng
 * @since 2021/9/22 16:00
 */
public class Result implements Serializable {
    private Map<String,Object> before;
    private Map<String,Object> after;
    private Map<String,Object> source;
    private Object op;
    private Object ts_ms;
    private Object transaction;

    public Map<String, Object> getBefore() {
        return before;
    }

    public void setBefore(Map<String, Object> before) {
        this.before = before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public void setAfter(Map<String, Object> after) {
        this.after = after;
    }

    public Map<String, Object> getSource() {
        return source;
    }

    public void setSource(Map<String, Object> source) {
        this.source = source;
    }

    public Object getOp() {
        return op;
    }

    public void setOp(Object op) {
        this.op = op;
    }

    public Object getTs_ms() {
        return ts_ms;
    }

    public void setTs_ms(Object ts_ms) {
        this.ts_ms = ts_ms;
    }

    public Object getTransaction() {
        return transaction;
    }

    public void setTransaction(Object transaction) {
        this.transaction = transaction;
    }
}
