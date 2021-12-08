package cn.huaming.flink.udf;

import cn.huaming.flink.entity.Access;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

public class GaodeLocationMapFunction extends RichMapFunction<Access, Access> {

    private static final String GAODE_KEY = "f22ac035dd9a10b7ad4251d194f66e40";

    private static final String CITY = "city";
    private static final String PROVINCE = "province";

    CloseableHttpClient httpClient;

    @Override
    public void open(Configuration parameters) throws Exception {
        httpClient = HttpClients.createDefault();
    }

    @Override
    public void close() throws Exception {
        if (httpClient != null) httpClient.close();
    }

    @Override
    public Access map(Access value) throws Exception {
        String url = "https://restapi.amap.com/v3/ip?ip=" + value.getIp() + "&output=json&key=" + GAODE_KEY;

        String province = "-";
        String city = "-";

        HttpGet httpGet = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                String result = EntityUtils.toString(entity, StandardCharsets.UTF_8);

                JSONObject jsonObject = JSON.parseObject(result);
                province = jsonObject.getString(PROVINCE);
                city = jsonObject.getString(CITY);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            value.setProvince(province);
            value.setCity(city);
        }

        return value;
    }
}
