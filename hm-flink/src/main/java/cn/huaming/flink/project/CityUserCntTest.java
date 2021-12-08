package cn.huaming.flink.project;

import cn.huaming.flink.entity.Access;
import cn.huaming.flink.udf.GaodeLocationMapFunction;
import com.alibaba.fastjson.JSON;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;

import java.util.Objects;

/**
 * 按照城市维度进行用户的统计 并存入Redis
 */
public class CityUserCntTest {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();

        SingleOutputStreamOperator<Access> cleanStream =
        environment.readTextFile("hm-flink/data-file/access.log")
                .map(new MapFunction<String, Access>() {
                    @Override
                    public Access map(String value) throws Exception {
                        try {
                            return JSON.parseObject(value, Access.class);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }

                    }
                }).filter(Objects::nonNull)
                .map(new GaodeLocationMapFunction());

        SingleOutputStreamOperator<Tuple3<String, String, Integer>> result = cleanStream.map(new MapFunction<Access, Tuple3<String, String, Integer>>() {
                    @Override
                    public Tuple3<String, String, Integer> map(Access value) throws Exception {
                        return Tuple3.of(value.getProvince(), value.getCity(), 1);
                    }
                })
                .keyBy(new KeySelector<Tuple3<String, String, Integer>, Tuple2<String, String>>() {
                    @Override
                    public Tuple2<String, String> getKey(Tuple3<String, String, Integer> value) throws Exception {
                        return Tuple2.of(value.f0, value.f1);
                    }
                })
                .sum(2);//.print().setParallelism(1);


        FlinkJedisPoolConfig conf = new FlinkJedisPoolConfig.Builder()
                .setHost("192.168.1.223")
                .setPassword("iSc!23465Test83")
                .setDatabase(11)
                .setPort(6379)
                .build();

        result.addSink(new RedisSink<Tuple3<String, String, Integer>>(conf, new RedisExampleMapper()));


        environment.execute("CityUserCntTest");

    }

    static class RedisExampleMapper implements RedisMapper<Tuple3<String, String, Integer>> {

        @Override
        public RedisCommandDescription getCommandDescription() {
            return new RedisCommandDescription(RedisCommand.HSET, "city-user-cnt");
        }

        @Override
        public String getKeyFromData(Tuple3<String, String, Integer> data) {
            return data.f0 + "_" + data.f1;
        }

        @Override
        public String getValueFromData(Tuple3<String, String, Integer> data) {
            return data.f2 + "";
        }

    }
}
