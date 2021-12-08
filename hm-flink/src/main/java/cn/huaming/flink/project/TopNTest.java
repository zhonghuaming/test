package cn.huaming.flink.project;

import cn.huaming.flink.entity.Event;
import cn.huaming.flink.entity.EventCount;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.guava30.com.google.common.collect.Lists;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.time.DateUtils.MILLIS_PER_SECOND;

/**
 * 时间以毫秒为单位
 * <p>
 * 统计不同事件类型、商品的Top n 访问量
 * Siding Window + Watermarks
 * 窗口大小5min,滑动步长1min
 *
 * @auther: ZHM
 * @date: 2021/12/6 16:56
 */

@Slf4j
public class TopNTest {

    private static final FastDateFormat instance = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.readTextFile("hm-flink\\data-file\\event.log")
                .map(new MapFunction<String, Event>() {
                    @Override
                    public Event map(String value) {
                        try {
                            return JSON.parseObject(value, Event.class);
                        } catch (Exception e) {
                            log.error("value:{}", value);
                        }
                        return null;
                    }
                })
                .filter(x -> !Objects.isNull(x))
                //设置水印
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<Event>(Time.seconds(30)) {
                    @Override
                    public long extractTimestamp(Event element) {
                        long l = Long.parseLong(element.getCreatedAt());
                        log.debug("获取时间：{}", instance.format(l * MILLIS_PER_SECOND));
                        return l;
                    }
                })
                //过滤启动的事件数据
                .filter(new FilterFunction<Event>() {
                    @Override
                    public boolean filter(Event value) throws Exception {
                        return !"startup".equals(value.getEvent());
                    }
                })
                //以事件+产品名 为key
                .keyBy(new KeySelector<Event, Tuple2<String, String>>() {
                    @Override
                    public Tuple2<String, String> getKey(Event value) throws Exception {
                        return Tuple2.of(value.getEvent(), value.getProductName());
                    }
                })
                //开启窗户
                .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
                //以窗口为单位聚合
                .aggregate(new AggregateFunction<Event, Long, Long>() {
                    @Override
                    public Long createAccumulator() {
                        return 0L;
                    }

                    @Override
                    public Long add(Event value, Long accumulator) {
                        return accumulator + 1;
                    }

                    @Override
                    public Long getResult(Long accumulator) {
                        return accumulator;
                    }

                    @Override
                    public Long merge(Long a, Long b) {
                        return null;
                    }
                }, new WindowFunction<Long, EventCount, Tuple2<String, String>, TimeWindow>() {
                    @Override
                    public void apply(Tuple2<String, String> value, TimeWindow timeWindow,
                                      Iterable<Long> iterable, Collector<EventCount> collector) throws Exception {
                        String event = value.f0;
                        String productName = value.f1;
//                        String start = instance.format(timeWindow.getStart());
//                        String end = instance.format(timeWindow.getEnd());
                        long start = timeWindow.getStart();
                        long end = timeWindow.getEnd();
                        Long count = iterable.iterator().next();
                        collector.collect(
                                EventCount.builder().event(event).productName(productName).windowStart(start).count(count).windowEnd(end).build()
                        );
                    }
                })
                //记录状态，算出 一段时间内event,name 的top n
                .keyBy(new KeySelector<EventCount, Tuple3<String, Long, Long>>() {
                    @Override
                    public Tuple3<String, Long, Long> getKey(EventCount value) throws Exception {
                        return Tuple3.of(value.getEvent(), value.getWindowStart(), value.getWindowEnd());
                    }
                }).process(new KeyedProcessFunction<Tuple3<String, Long, Long>, EventCount, List<EventCount>>() {

                    private transient ListState<EventCount> listState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        listState = getRuntimeContext().getListState(new ListStateDescriptor<EventCount>("cnt-state", EventCount.class));
                    }

                    @Override
                    public void processElement(EventCount eventCount, KeyedProcessFunction<Tuple3<String, Long, Long>, EventCount,
                            List<EventCount>>.Context context, Collector<List<EventCount>> collector) throws Exception {
                        listState.add(eventCount);

                        //注册一个定时器
                        context.timerService().registerEventTimeTimer(eventCount.getWindowEnd() + 1);
                    }

                    //完成top n
                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<Tuple3<String, Long, Long>, EventCount,
                            List<EventCount>>.OnTimerContext ctx, Collector<List<EventCount>> out) throws Exception {

                        int top = 3;

                        ArrayList<EventCount> eventCounts = Lists.newArrayList(listState.get());
                        eventCounts.sort((x, y) -> Long.compare(y.getCount(), x.getCount()));

                        ArrayList<EventCount> sorted = new ArrayList<>();

                        for (int i = 0; i < Math.min(top, eventCounts.size()); i++) {
                            sorted.add(eventCounts.get(i));
                        }

                        out.collect(sorted);
                    }

                })
                .setParallelism(1).print();

        env.execute();
    }
}
