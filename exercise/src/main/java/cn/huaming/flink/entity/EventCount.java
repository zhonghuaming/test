package cn.huaming.flink.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @auther: ZHM
 * @date: 2021/12/6 18:24
 */
@Data
@Builder
public class EventCount {

    private String event;
    private String productName;
    private long count;
    private String windowStart;
    private String windowEnd;



}
