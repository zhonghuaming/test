package cn.huaming.flink.project;

import cn.huaming.flink.entity.Access;
import com.alibaba.fastjson.JSON;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.guava30.com.google.common.hash.BloomFilter;
import org.apache.flink.shaded.guava30.com.google.common.hash.Funnels;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.Objects;

/**
 * 用户是否用该设备类型登录登陆 的统计分析
 * <p>
 * 根据每个每个设备类型、uid来进行判断是否是第一次登录用户
 * <p>
 * 思考：uid放到state里面
 * <p>
 * 实现：状态 + 布隆过滤器
 */
public class DeviceLoginCntTest {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();

        SingleOutputStreamOperator<Access> cleanStream = environment.readTextFile("hm-flink/data-file/access.log")
                .map(new MapFunction<String, Access>() {
                    @Override
                    public Access map(String value) throws Exception {
                        try {
                            Access access = JSON.parseObject(value, Access.class);

                            UserAgent userAgent = UserAgent.parseUserAgentString(access.getAgent());

                            OperatingSystem operatingSystem = userAgent.getOperatingSystem();

                            access.setDeviceType(operatingSystem.getDeviceType().getName());

                            return access;
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }

                    }
                }).filter(Objects::nonNull);


        cleanStream.keyBy(Access::getDeviceType)
                .process(new KeyedProcessFunction<String, Access, Access>() {

                    private transient ValueState<BloomFilter<String>> state;

                    @Override
                    public void open(Configuration parameters) throws Exception {

                        ValueStateDescriptor<BloomFilter<String>> descriptor = new ValueStateDescriptor<>("s",
                                TypeInformation.of(new TypeHint<BloomFilter<String>>() {
                                }));

                        state = getRuntimeContext().getState(descriptor);
                    }

                    @Override
                    public void processElement(Access value, Context ctx, Collector<Access> out) throws Exception {
                        String device = value.getUid();
                        BloomFilter<String> bloomFilter = state.value();
                        if (null == bloomFilter) {
                            bloomFilter = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100000);
                        }

                        if (!bloomFilter.mightContain(device)) {
                            bloomFilter.put(device);
                            value.setFirstLogin(true);
                            state.update(bloomFilter);
                        }

                        out.collect(value);
                    }
                }).print();


        environment.execute("DeviceLoginCntTest");

    }
}
