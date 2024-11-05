package com.cleaner.djuav.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cleaner.djuav.constant.FileTypeConstants;
import com.cleaner.djuav.domain.PointActionReq;
import com.cleaner.djuav.domain.RoutePointReq;
import com.cleaner.djuav.domain.kml.*;
import com.cleaner.djuav.enums.kml.*;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RouteFileUtils {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String LOCAL_KMZ_FILE_PATH = "file" + File.separator + "kmz" + File.separator;


    /**
     * kml文件解析
     *
     * @param inputStream
     * @return
     */
    public static KmlInfo parseKml(InputStream inputStream) {
        XStream xStream = new XStream();
        xStream.allowTypes(new Class[]{KmlInfo.class, KmlAction.class, KmlWayLineCoordinateSysParam.class, KmlPoint.class});
        xStream.alias("kml", KmlInfo.class);
        xStream.processAnnotations(KmlInfo.class);
        xStream.autodetectAnnotations(true);
        xStream.addImplicitCollection(KmlActionGroup.class, "action");
        return (KmlInfo) xStream.fromXML(inputStream);
    }

    /**
     * 生成kmz文件
     */
    public static String buildKmz(String fileName, KmlParams kmlParams) {
        KmlInfo kmlInfo = buildKml(kmlParams);
        KmlInfo wpmlInfo = buildWpml(kmlParams);
        return buildKmz(fileName, kmlInfo, wpmlInfo);
    }

    public static String buildKmz(String fileName, KmlInfo kmlInfo, KmlInfo wpmlInfo) {
        XStream xStream = new XStream(new DomDriver());
        xStream.processAnnotations(KmlInfo.class);
        xStream.addImplicitCollection(KmlActionGroup.class, "action");

        String kml = XML_HEADER + xStream.toXML(kmlInfo);
        String wpml = XML_HEADER + xStream.toXML(wpmlInfo);

        try (FileOutputStream fileOutputStream = new FileOutputStream(LOCAL_KMZ_FILE_PATH + fileName + ".kmz");
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            zipOutputStream.setLevel(0); // 0 表示不压缩，存储方式

            // 创建 wpmz 目录中的 template.kml 文件条目
            ZipEntry kmlEntry = new ZipEntry("wpmz/template.kml");
            zipOutputStream.putNextEntry(kmlEntry);
            // 将内容写入 ZIP 条目
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(kml.getBytes(StandardCharsets.UTF_8))) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) >= 0) {
                    zipOutputStream.write(buffer, 0, length);
                }
            }
            zipOutputStream.closeEntry(); // 关闭条目

            // 创建 wpmz 目录中的 waylines.wpml 文件条目
            ZipEntry wpmlEntry = new ZipEntry("wpmz/waylines.wpml");
            zipOutputStream.putNextEntry(wpmlEntry);
            // 将内容写入 ZIP 条目
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(wpml.getBytes(StandardCharsets.UTF_8))) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) >= 0) {
                    zipOutputStream.write(buffer, 0, length);
                }
            }
            zipOutputStream.closeEntry(); // 关闭条目

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return LOCAL_KMZ_FILE_PATH + fileName + ".kmz";
    }


    public static KmlInfo buildKml(KmlParams kmlParams) {
        KmlInfo kmlInfo = new KmlInfo();
        kmlInfo.setDocument(buildKmlDocument(FileTypeConstants.KML, kmlParams));
        return kmlInfo;
    }

    public static KmlInfo buildWpml(KmlParams kmlParams) {
        KmlInfo kmlInfo = new KmlInfo();
        kmlInfo.setDocument(buildKmlDocument(FileTypeConstants.WPML, kmlParams));
        return kmlInfo;
    }

    public static KmlDocument buildKmlDocument(String fileType, KmlParams kmlParams) {
        KmlDocument kmlDocument = new KmlDocument();
        if (StringUtils.equals(fileType, FileTypeConstants.KML)) {
            kmlDocument.setAuthor("Cleaner");
            kmlDocument.setCreateTime(String.valueOf(DateUtil.current()));
            kmlDocument.setUpdateTime(String.valueOf(DateUtil.current()));
        }
        kmlDocument.setKmlMissionConfig(buildKmlMissionConfig(kmlParams));
        kmlDocument.setFolder(buildKmlFolder(fileType, kmlParams));
        return kmlDocument;
    }

    public static KmlMissionConfig buildKmlMissionConfig(KmlParams kmlParams) {
        KmlMissionConfig kmlMissionConfig = new KmlMissionConfig();
        kmlMissionConfig.setFlyToWayLineMode(FlyToWaylineModeEnums.SAFELY.getValue());
        kmlMissionConfig.setFinishAction(FinishActionEnums.GO_HOME.getValue());
        kmlMissionConfig.setExitOnRCLost(ExitOnRCLostEnums.EXECUTE_LOST_ACTION.getValue());
        kmlMissionConfig.setExecuteRCLostAction(ExecuteRCLostActionEnums.GO_BACK.getValue());
        kmlMissionConfig.setTakeOffSecurityHeight("20");
        kmlMissionConfig.setGlobalTransitionalSpeed("15");
        kmlMissionConfig.setGlobalRTHHeight("100");
        kmlMissionConfig.setDroneInfo(buildKmlDroneInfo(kmlParams.getDroneType(), kmlParams.getSubDroneType()));
        kmlMissionConfig.setPayloadInfo(buildKmlPayloadInfo(kmlParams.getPayloadType(), kmlParams.getPayloadPosition()));
        return kmlMissionConfig;
    }

    public static KmlDroneInfo buildKmlDroneInfo(Integer droneType, Integer subDroneType) {
        KmlDroneInfo kmlDroneInfo = new KmlDroneInfo();
        kmlDroneInfo.setDroneEnumValue(String.valueOf(droneType));
        if (Objects.equals(droneType, DroneTypeEnums.M30_M30T.getValue()) ||
                Objects.equals(droneType, DroneTypeEnums.M3D_M3TD.getValue()) ||
                Objects.equals(droneType, DroneTypeEnums.M3E_M3T_M3M.getValue())) {
            kmlDroneInfo.setDroneSubEnumValue(String.valueOf(subDroneType));
        }
        return kmlDroneInfo;
    }

    public static KmlPayloadInfo buildKmlPayloadInfo(Integer payloadType, Integer payloadPosition) {
        KmlPayloadInfo kmlPayloadInfo = new KmlPayloadInfo();
        kmlPayloadInfo.setPayloadEnumValue(String.valueOf(payloadType));
        kmlPayloadInfo.setPayloadPositionIndex(String.valueOf(payloadPosition));
        return kmlPayloadInfo;

    }


    public static KmlFolder buildKmlFolder(String fileType, KmlParams kmlParams) {
        KmlFolder kmlFolder = new KmlFolder();
        kmlFolder.setTemplateId("0");
        kmlFolder.setAutoFlightSpeed("10");
        if (StringUtils.equals(fileType, FileTypeConstants.KML)) {
            kmlFolder.setTemplateType(TemplateTypeEnums.WAYPOINT.getValue());
            kmlFolder.setWaylineCoordinateSysParam(buildKmlWayLineCoordinateSysParam(TemplateTypeEnums.WAYPOINT.getValue()));
            kmlFolder.setPayloadParam(buildKmlPayloadParam(kmlParams.getPayloadPosition()));
        }
        if (StringUtils.equals(fileType, FileTypeConstants.WPML)) {
            kmlFolder.setWaylineId("0");
            kmlFolder.setExecuteHeightMode(ExecuteHeightModeEnums.RELATIVE_TO_START_POINT.getValue());
        }
        // 航点类型航线配置
        if (StringUtils.equals(kmlFolder.getTemplateType(), TemplateTypeEnums.WAYPOINT.getValue())) {
            kmlFolder.setGlobalWaypointTurnMode(GlobalWaypointTurnModeEnums.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE.getValue());
            kmlFolder.setGlobalUseStraightLine("1");
            kmlFolder.setGimbalPitchMode(GimbalPitchModeEnums.MANUAL.getValue());
            kmlFolder.setGlobalHeight("100");
            kmlFolder.setGlobalWaypointHeadingParam(buildKmlGlobalWaypointHeadingParam(WaypointHeadingModeEnums.FOLLOW_WAYLINE.getValue(), 45.0, "24.323345,116.324532,31.000000"));
        }
        // 构建航点
        List<KmlPlacemark> kmlPlacemarkList = new ArrayList<>();
        List<PointActionReq> kmlActionList = new ArrayList<>();
        PointActionReq pointActionReq = new PointActionReq();
        pointActionReq.setActionIndex(1);
        pointActionReq.setHoverTime(10);
        kmlActionList.add(pointActionReq);
        for (int i = 0; i < 2; i++) {
            RoutePointReq routePointReq = new RoutePointReq();
            routePointReq.setRoutePointIndex(i);
            routePointReq.setLongitude(123.23);
            routePointReq.setLatitude(45.45);
            routePointReq.setActions(kmlActionList);
            kmlPlacemarkList.add(buildKmlPlacemark(routePointReq, kmlParams,fileType));
        }
        kmlFolder.setPlacemarkList(kmlPlacemarkList);
        return kmlFolder;
    }

    public static KmlWayLineCoordinateSysParam buildKmlWayLineCoordinateSysParam(String templateType) {
        KmlWayLineCoordinateSysParam kmlWayLineCoordinateSysParam = new KmlWayLineCoordinateSysParam();
        kmlWayLineCoordinateSysParam.setCoordinateMode("WGS84");
        kmlWayLineCoordinateSysParam.setHeightMode(HeightModeEnums.RELATIVE_TO_START_POINT.getValue());
        kmlWayLineCoordinateSysParam.setPositioningType(PositioningTypeEnums.GPS.getValue());
        if (StringUtils.equals(templateType, TemplateTypeEnums.MAPPING2D.getValue()) ||
                StringUtils.equals(templateType, TemplateTypeEnums.MAPPING3D.getValue()) ||
                StringUtils.equals(templateType, TemplateTypeEnums.MAPPING_STRIP.getValue())) {
            kmlWayLineCoordinateSysParam.setGlobalShootHeight("50");
            kmlWayLineCoordinateSysParam.setSurfaceFollowModeEnable("1");
            kmlWayLineCoordinateSysParam.setSurfaceRelativeHeight("100");
        }
        return kmlWayLineCoordinateSysParam;
    }

    public static KmlPayloadParam buildKmlPayloadParam(Integer payloadPosition) {
        KmlPayloadParam kmlPayloadParam = new KmlPayloadParam();
        kmlPayloadParam.setPayloadPositionIndex(String.valueOf(payloadPosition));
        kmlPayloadParam.setFocusMode(FocusModeEnums.FIRST_POINT.getValue());
        kmlPayloadParam.setMeteringMode(MeteringModeEnums.AVERAGE.getValue());
        // 0：不开启 1：开启
        kmlPayloadParam.setDewarpingEnable("1");
        kmlPayloadParam.setReturnMode(ReturnModeEnums.SINGLE_RETURN_STRONGEST.getValue());
        // 60000、80000、120000、160000、180000、240000
        kmlPayloadParam.setSamplingRate("240000");
        kmlPayloadParam.setScanningMode(ScanningModeEnums.REPETITIVE.getValue());
        // 0: 不上色 1: 真彩上色
        kmlPayloadParam.setModelColoringEnable("1");
        kmlPayloadParam.setImageFormat(ImageFormatEnums.WIDE.getValue() + "," + ImageFormatEnums.ZOOM.getValue());

        return kmlPayloadParam;
    }

    public static KmlGlobalWaypointHeadingParam buildKmlGlobalWaypointHeadingParam(String waypointHeadingMode, Double waypointHeadingAngle, String waypointPoiPoint) {
        KmlGlobalWaypointHeadingParam kmlGlobalWaypointHeadingParam = new KmlGlobalWaypointHeadingParam();
        kmlGlobalWaypointHeadingParam.setWaypointHeadingMode(waypointHeadingMode);
        if (StringUtils.equals(waypointHeadingMode, WaypointHeadingModeEnums.SMOOTH_TRANSITION.getValue())) {
            kmlGlobalWaypointHeadingParam.setWaypointHeadingAngle(String.valueOf(waypointHeadingAngle));
        }
        if (StringUtils.equals(waypointHeadingMode, WaypointHeadingModeEnums.TOWARD_POI.getValue())) {
            kmlGlobalWaypointHeadingParam.setWaypointPoiPoint(waypointPoiPoint);
        }
        kmlGlobalWaypointHeadingParam.setWaypointHeadingPathMode(WaypointHeadingPathModeEnums.FOLLOW_BAD_ARC.getValue());
        return kmlGlobalWaypointHeadingParam;

    }

    public static KmlPlacemark buildKmlPlacemark(RoutePointReq routePointReq, KmlParams kmlParams,String fileType) {
        KmlPlacemark kmlPlacemark = new KmlPlacemark();
        kmlPlacemark.setIsRisky("0");
        // 航点坐标系转换
        kmlPlacemark.setKmlPoint(buildKmlPoint(String.valueOf(routePointReq.getLongitude()), String.valueOf(routePointReq.getLatitude())));
        kmlPlacemark.setIndex(String.valueOf(routePointReq.getRoutePointIndex()));
        if (StringUtils.equals(fileType, FileTypeConstants.KML)) {
            kmlPlacemark.setUseGlobalHeight("1");
            kmlPlacemark.setUseGlobalSpeed("1");
            kmlPlacemark.setUseGlobalHeadingParam("1");
            kmlPlacemark.setUseGlobalTurnParam("1");
        } else if (StringUtils.equals(fileType, FileTypeConstants.WPML)) {
            kmlPlacemark.setExecuteHeight(String.valueOf(kmlParams.getExecuteHeight()));
            kmlPlacemark.setWaypointSpeed(String.valueOf(kmlParams.getAutoFlightSpeed()));
            kmlPlacemark.setWaypointHeadingParam(buildKmlWaypointHeadingParam());
            kmlPlacemark.setWaypointTurnParam(buildKmlWaypointTurnParam());
        }
        kmlPlacemark.setActionGroup(buildKmlActionGroup(routePointReq, kmlParams));
        return kmlPlacemark;
    }

    public static KmlPoint buildKmlPoint(String longitude, String latitude) {
        KmlPoint kmlPoint = new KmlPoint();
        kmlPoint.setCoordinates(longitude + "," + latitude);
        return kmlPoint;
    }

    public static KmlWaypointHeadingParam buildKmlWaypointHeadingParam() {
        KmlWaypointHeadingParam kmlWaypointHeadingParam = new KmlWaypointHeadingParam();
        kmlWaypointHeadingParam.setWaypointHeadingMode(WaypointHeadingModeEnums.FOLLOW_WAYLINE.getValue());
        kmlWaypointHeadingParam.setWaypointHeadingAngle("0");
        kmlWaypointHeadingParam.setWaypointPoiPoint("0,0,0");
        kmlWaypointHeadingParam.setWaypointHeadingPathMode(WaypointHeadingPathModeEnums.FOLLOW_BAD_ARC.getValue());
        return kmlWaypointHeadingParam;
    }

    public static KmlWaypointTurnParam buildKmlWaypointTurnParam() {
        KmlWaypointTurnParam kmlWaypointTurnParam = new KmlWaypointTurnParam();
        kmlWaypointTurnParam.setWaypointTurnMode(GlobalWaypointTurnModeEnums.TO_POINT_AND_STOP_WITH_DISCONTINUITY_CURVATURE.getValue());
        kmlWaypointTurnParam.setWaypointTurnDampingDist("0.2");
        return kmlWaypointTurnParam;
    }

    public static KmlActionGroup buildKmlActionGroup(RoutePointReq routePointReq, KmlParams kmlParams) {
        KmlActionGroup kmlActionGroup = new KmlActionGroup();
        kmlActionGroup.setActionGroupId(String.valueOf(routePointReq.getRoutePointIndex()));
        kmlActionGroup.setActionGroupStartIndex("0");
        kmlActionGroup.setActionGroupEndIndex("0");
        kmlActionGroup.setActionGroupMode(ActionGroupModeEnums.SEQUENCE.getValue());
        kmlActionGroup.setActionTrigger(buildKmlActionTrigger());
        List<KmlAction> kmlActionList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(routePointReq.getActions())) {
            for (PointActionReq pointActionReq : routePointReq.getActions()) {
                if (ObjectUtil.isNotNull(pointActionReq.getHoverTime())) {
                    kmlActionList.add(buildKmlAction(String.valueOf(pointActionReq.getActionIndex()), ActionActuatorFuncEnums.HOVER.getValue(), pointActionReq, kmlParams));
                } else if (ObjectUtil.isNotNull(pointActionReq.getAircraftHeading())) {
                    kmlActionList.add(buildKmlAction(String.valueOf(pointActionReq.getActionIndex()), ActionActuatorFuncEnums.ROTATE_YAW.getValue(), pointActionReq, kmlParams));
                } else if (ObjectUtil.isNotNull(pointActionReq.getUseGlobalImageFormat())) {
                    kmlActionList.add(buildKmlAction(String.valueOf(pointActionReq.getActionIndex()), ActionActuatorFuncEnums.TAKE_PHOTO.getValue(), pointActionReq, kmlParams));
                } else if ((ObjectUtil.isNotNull(pointActionReq.getGimbalYawRotateAngle())) || (ObjectUtil.isNotNull(pointActionReq.getGimbalPitchRotateAngle()))) {
                    kmlActionList.add(buildKmlAction(String.valueOf(pointActionReq.getActionIndex()), ActionActuatorFuncEnums.GIMBAL_ROTATE.getValue(), pointActionReq, kmlParams));
                } else if (ObjectUtil.isNotNull(pointActionReq.getZoom())) {
                    kmlActionList.add(buildKmlAction(String.valueOf(pointActionReq.getActionIndex()), ActionActuatorFuncEnums.ZOOM.getValue(), pointActionReq, kmlParams));
                }
            }
        }
        kmlActionGroup.setAction(kmlActionList);
        return kmlActionGroup;
    }

    public static KmlActionTrigger buildKmlActionTrigger() {
        KmlActionTrigger kmlActionTrigger = new KmlActionTrigger();
        kmlActionTrigger.setActionTriggerType(ActionTriggerTypeEnums.REACH_POINT.getValue());
        return kmlActionTrigger;
    }

    public static KmlAction buildKmlAction(String actionId, String actionActuatorFunc, PointActionReq pointActionReq, KmlParams kmlParams) {
        KmlAction kmlAction = new KmlAction();
        kmlAction.setActionId(actionId);
        kmlAction.setActionActuatorFunc(actionActuatorFunc);
        kmlAction.setActionActuatorFuncParam(buildKmlActionActuatorFuncParam(actionActuatorFunc, pointActionReq, kmlParams));
        return kmlAction;
    }

    public static KmlActionActuatorFuncParam buildKmlActionActuatorFuncParam(String actionActuatorFunc, PointActionReq pointActionReq, KmlParams kmlParams) {
        KmlActionActuatorFuncParam kmlActionActuatorFuncParam = new KmlActionActuatorFuncParam();
        if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.TAKE_PHOTO.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setFileSuffix("");
            kmlActionActuatorFuncParam.setUseGlobalPayloadLensIndex(String.valueOf(pointActionReq.getUseGlobalImageFormat()));
            if (ObjectUtil.equals(pointActionReq.getUseGlobalImageFormat(), 0)) {
                kmlActionActuatorFuncParam.setPayloadLensIndex(pointActionReq.getImageFormat());
            } else {
                kmlActionActuatorFuncParam.setPayloadLensIndex(kmlParams.getImageFormat());
            }
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.GIMBAL_ROTATE.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setGimbalHeadingYawBase("north");
            kmlActionActuatorFuncParam.setGimbalRotateMode("absoluteAngle");
            if (!Objects.isNull(pointActionReq.getGimbalPitchRotateAngle())) {
                kmlActionActuatorFuncParam.setGimbalPitchRotateEnable("1");
                kmlActionActuatorFuncParam.setGimbalPitchRotateAngle(String.valueOf(pointActionReq.getGimbalPitchRotateAngle()));
            } else {
                kmlActionActuatorFuncParam.setGimbalPitchRotateEnable("0");
                kmlActionActuatorFuncParam.setGimbalPitchRotateAngle("0");
            }
            kmlActionActuatorFuncParam.setGimbalRollRotateEnable("0");
            kmlActionActuatorFuncParam.setGimbalRollRotateAngle("0");
            if (!Objects.isNull(pointActionReq.getGimbalYawRotateAngle())) {
                kmlActionActuatorFuncParam.setGimbalYawRotateEnable("1");
                kmlActionActuatorFuncParam.setGimbalYawRotateAngle(String.valueOf(pointActionReq.getGimbalYawRotateAngle()));
            } else {
                kmlActionActuatorFuncParam.setGimbalYawRotateEnable("0");
                kmlActionActuatorFuncParam.setGimbalYawRotateAngle("0");
            }
            kmlActionActuatorFuncParam.setGimbalRotateTimeEnable("0");
            kmlActionActuatorFuncParam.setGimbalRotateTime("0");
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ROTATE_YAW.getValue())) {
            kmlActionActuatorFuncParam.setAircraftHeading(String.valueOf(pointActionReq.getAircraftHeading()));
            kmlActionActuatorFuncParam.setAircraftPathMode(AircraftPathModeEnums.CLOCKWISE.getValue());
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.HOVER.getValue())) {
            kmlActionActuatorFuncParam.setHoverTime(String.valueOf(pointActionReq.getHoverTime()));
        } else if (StringUtils.equals(actionActuatorFunc, ActionActuatorFuncEnums.ZOOM.getValue())) {
            kmlActionActuatorFuncParam.setPayloadPositionIndex(String.valueOf(kmlParams.getPayloadPosition()));
            kmlActionActuatorFuncParam.setFocalLength(String.valueOf(pointActionReq.getZoom()));
        }
        return kmlActionActuatorFuncParam;
    }
}