package com.cleaner.djuav.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class PointActionReq implements Serializable {
    /**
     * 动作编号
     */
    private Integer actionIndex;

    /**
     * 飞行器悬停等待时间
     */
    private Integer hoverTime;

    /**
     * 飞行器目标偏航角
     */
    private Double aircraftHeading;

    /**
     * 是否使用全局拍照模式 0：不使用 1：使用
     */
    private Integer useGlobalImageFormat;

    /**
     * 拍照模式（字典）
     */
    private String imageFormat;

    /**
     * 云台偏航角
     */
    private Double gimbalYawRotateAngle;

    /**
     * 云台俯仰角
     */
    private Double gimbalPitchRotateAngle;

    /**
     * 变焦焦距
     */
    private Double zoom;

}