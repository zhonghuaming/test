package cn.huaming.flink.entity;

import lombok.Data;

@Data
public class Access {
    private String uid;
    private String ip;
    private String agent;
    private String deviceType;
    private String createdAt;
    private String province;
    private String city;
    private boolean firstLogin;

    @Override
    public String toString() {
        return "Access{" +
                "deviceType='" + deviceType + '\'' +
                ", uid='" + uid + '\'' +
                ", firstLogin=" + firstLogin +
                ", ip='" + ip + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
