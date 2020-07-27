package com.leeyom.sdk.tencent.lbs.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.leeyom.sdk.base.BizException;
import com.leeyom.sdk.tencent.lbs.config.WebServiceApiConst;
import com.leeyom.sdk.tencent.lbs.config.WebServiceApiProperties;
import com.leeyom.sdk.tencent.lbs.config.WebServiceUri;
import com.leeyom.sdk.tencent.lbs.dto.*;
import com.leeyom.sdk.tencent.lbs.exception.LbsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TencentLbsUtil {


    private static WebServiceApiProperties webServiceApiProperties;

    @Autowired
    public void setWebServiceApiProperties(WebServiceApiProperties webServiceApiProperties) {
        TencentLbsUtil.webServiceApiProperties = webServiceApiProperties;
    }

    /**
     * 地理坐标转具体的地址信息
     *
     * @param location 地理坐标，比如：39.984154,116.307490
     * @return 具体的地址信息
     */
    public static String locationToAddress(String location) {
        if (StrUtil.isBlank(location)) {
            throw new BizException("地理坐标不能为空");
        }
        Map<String, Object> param = new HashMap<>(3);
        param.put(WebServiceApiConst.LOCATION, location);
        TencentMapResultDTO resultDTO = requestToTencent(param, WebServiceUri.GEOCODER_API);
        TencentMapAddressDTO tencentMapAddressDTO = BeanUtil.toBean(resultDTO.getResult(), TencentMapAddressDTO.class);
        if (tencentMapAddressDTO == null) {
            throw new LbsException(resultDTO.getMessage());
        }
        return tencentMapAddressDTO.getAddress();
    }

    /**
     * 具体的地址信息转经纬度坐标
     *
     * @param address 详细地址，比如：北京市海淀区彩和坊路海淀西大街74号
     * @return 经纬度
     */
    public static LocationDTO addressToLocation(String address) {
        if (StrUtil.isBlank(address)) {
            throw new BizException("详细地址不能为空");
        }
        Map<String, Object> param = new HashMap<>(3);
        param.put(WebServiceApiConst.ADDRESS, address);
        TencentMapResultDTO resultDTO = requestToTencent(param, WebServiceUri.GEOCODER_API);
        TencentMapLocationDTO tencentMapLocationDTO = BeanUtil.toBean(resultDTO.getResult(), TencentMapLocationDTO.class);
        if (tencentMapLocationDTO == null) {
            throw new LbsException(resultDTO.getMessage());
        }
        return tencentMapLocationDTO.getLocation();
    }

    /**
     * 用于单起点到多终点，或多起点到单终点的路线距离（非直线距离）计算；
     * 起点到终点最大限制直线距离10公里，一般用于O2O上门服务
     *
     * @param mode 计算方式：driving（驾车）、walking（步行）
     * @param from 起点坐标，例如：39.071510,117.190091
     * @param to   终点坐标，经度与纬度用英文逗号分隔，坐标间用英文分号分隔
     * @return 起点到终点的距离，单位：km
     */
    public static double distance(String mode, String from, String to) {
        validateParam(mode, from, to);
        Map<String, Object> param = new HashMap<>(5);
        param.put(WebServiceApiConst.MODE, mode);
        param.put(WebServiceApiConst.FROM, from);
        param.put(WebServiceApiConst.TO, to);
        TencentMapResultDTO resultDTO = requestToTencent(param, WebServiceUri.DISTANCE_API);
        if (resultDTO.getStatus() != 0) {
            throw new LbsException(resultDTO.getMessage());
        }
        TencentMapDistanceDTO tencentMapDistanceDTO = BeanUtil.toBean(resultDTO.getResult(), TencentMapDistanceDTO.class);
        List<DistanceDTO> elements = tencentMapDistanceDTO.getElements();
        if (CollUtil.isNotEmpty(elements)) {
            DistanceDTO distanceDTO = elements.get(0);
            // 转km，保留两位小数
            double distance = distanceDTO.getDistance() / 1000;
            return NumberUtil.round(distance, 2).doubleValue();
        }
        return 0.00d;
    }

    private static void validateParam(String mode, String from, String to) {
        if (StrUtil.isBlank(mode)) {
            throw new BizException("计算方式不能为空");
        }

        if (StrUtil.isBlank(from)) {
            throw new BizException("起点坐标不能为空");
        }

        if (StrUtil.isBlank(to)) {
            throw new BizException("终点坐标不能为空");
        }
    }

    /**
     * 通过终端设备IP地址获取其当前所在地理位置，精确到市级，
     * 常用于显示当地城市天气预报、初始化用户城市等非精确定位场景。
     *
     * @param ip 比如：61.135.17.68
     * @return 当前ip的地理位置信息
     */
    public static IpLocationDTO ipLocation(String ip) {
        if (StrUtil.isBlank(ip)) {
            throw new BizException("ip不能为空");
        }
        Map<String, Object> param = CollUtil.newHashMap(3);
        param.put(WebServiceApiConst.IP, ip);
        TencentMapResultDTO resultDTO = requestToTencent(param, WebServiceUri.IP_API);
        if (resultDTO.getStatus() != 0) {
            throw new LbsException(resultDTO.getMessage());
        }
        return BeanUtil.toBean(resultDTO.getResult(), IpLocationDTO.class);
    }

    private static TencentMapResultDTO requestToTencent(Map<String, Object> param, String uri) {
        // 签名
        String sign = WebServiceApiSignUtil.signGetRequest(uri, webServiceApiProperties.getAppSecret(),
                webServiceApiProperties.getAppKey(), param);
        param.put(WebServiceApiConst.SIG, sign);
        String url = webServiceApiProperties.getDomain() + uri;
        String postResult = HttpUtil.get(url, param);
        return JSONUtil.toBean(postResult, TencentMapResultDTO.class);
    }


}