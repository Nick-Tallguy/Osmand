package net.osmand.plus.settings.backend.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.GPXUtilities.GPXTrackAnalysis;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.util.Algorithms;

import org.json.JSONException;
import org.json.JSONObject;

public class GpxAppearanceInfo {

	public String width;
	public GradientScaleType scaleType;
	public int color;
	public int gradientSpeedColor;
	public int gradientAltitudeColor;
	public int gradientSlopeColor;
	public int splitType;
	public double splitInterval;
	public boolean showArrows;
	public boolean showStartFinish;

	public long timeSpan;
	public int wptPoints;
	public float totalDistance;

	public GpxAppearanceInfo() {

	}

	public GpxAppearanceInfo(@NonNull GpxDataItem dataItem) {
		color = dataItem.getColor();
		width = dataItem.getWidth();
		showArrows = dataItem.isShowArrows();
		showStartFinish = dataItem.isShowStartFinish();
		splitType = dataItem.getSplitType();
		splitInterval = dataItem.getSplitInterval();
		scaleType = dataItem.getGradientScaleType();
		gradientSpeedColor = dataItem.getGradientSpeedColor();
		gradientSlopeColor = dataItem.getGradientSlopeColor();
		gradientAltitudeColor = dataItem.getGradientAltitudeColor();

		GPXTrackAnalysis analysis = dataItem.getAnalysis();
		if (analysis != null) {
			timeSpan = analysis.timeSpan;
			wptPoints = analysis.wptPoints;
			totalDistance = analysis.totalDistance;
		}
	}

	public void toJson(@NonNull JSONObject json) throws JSONException {
		writeParam(json, "color", color);
		writeParam(json, "width", width);
		writeParam(json, "show_arrows", showArrows);
		writeParam(json, "show_start_finish", showStartFinish);
		writeParam(json, "split_type", GpxSplitType.getSplitTypeByTypeId(splitType).getTypeName());
		writeParam(json, "split_interval", splitInterval);
		writeParam(json, "gradient_scale_type", scaleType);
		writeParam(json, GradientScaleType.SPEED.getColorTypeName(), gradientSpeedColor);
		writeParam(json, GradientScaleType.SLOPE.getColorTypeName(), gradientSlopeColor);
		writeParam(json, GradientScaleType.ALTITUDE.getColorTypeName(), gradientAltitudeColor);

		writeParam(json, "time_span", timeSpan);
		writeParam(json, "wpt_points", wptPoints);
		writeParam(json, "total_distance", totalDistance);
	}

	public static GpxAppearanceInfo fromJson(@NonNull JSONObject json) {
		GpxAppearanceInfo gpxAppearanceInfo = new GpxAppearanceInfo();
		gpxAppearanceInfo.color = json.optInt("color");
		gpxAppearanceInfo.width = json.optString("width");
		gpxAppearanceInfo.showArrows = json.optBoolean("show_arrows");
		gpxAppearanceInfo.showStartFinish = json.optBoolean("show_start_finish");
		gpxAppearanceInfo.splitType = GpxSplitType.getSplitTypeByName(json.optString("split_type")).getType();
		gpxAppearanceInfo.splitInterval = json.optDouble("split_interval");
		gpxAppearanceInfo.scaleType = getScaleType(json.optString("gradient_scale_type"));
		gpxAppearanceInfo.gradientSpeedColor = json.optInt(GradientScaleType.SPEED.getColorTypeName());
		gpxAppearanceInfo.gradientSlopeColor = json.optInt(GradientScaleType.SLOPE.getColorTypeName());
		gpxAppearanceInfo.gradientAltitudeColor = json.optInt(GradientScaleType.ALTITUDE.getColorTypeName());

		gpxAppearanceInfo.timeSpan = json.optLong("time_span");
		gpxAppearanceInfo.wptPoints = json.optInt("wpt_points");
		gpxAppearanceInfo.totalDistance = (float) json.optDouble("total_distance");

		return gpxAppearanceInfo;
	}

	private static GradientScaleType getScaleType(String name) {
		if (!Algorithms.isEmpty(name)) {
			try {
				return GradientScaleType.valueOf(name);
			} catch (IllegalStateException e) {
				SettingsHelper.LOG.error("Failed to read gradientScaleType", e);
			}
		}
		return null;
	}

	private static void writeParam(@NonNull JSONObject json, @NonNull String name, @Nullable Object value) throws JSONException {
		if (value instanceof Integer) {
			if ((Integer) value != 0) {
				json.putOpt(name, value);
			}
		} else if (value instanceof Long) {
			if ((Long) value != 0) {
				json.putOpt(name, value);
			}
		} else if (value instanceof Double) {
			if ((Double) value != 0.0 && !Double.isNaN((Double) value)) {
				json.putOpt(name, value);
			}
		} else if (value instanceof String) {
			if (!Algorithms.isEmpty((String) value)) {
				json.putOpt(name, value);
			}
		} else if (value != null) {
			json.putOpt(name, value);
		}
	}
}