/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateUtils;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final String DEGREE_CHARACTER = "\u00B0";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHoursMinutesTextPaint;
        Paint mSecondsTextPaint;
        Paint mDateTextPaint;
        Paint mHighTempTextPaint;
        Paint mLowTempTextPaint;

        String mCurrentWeather = KEY_WEATHER_CLEAR;
        int mHighTemp;
        int mLowTemp;

        boolean mAmbient;
        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        float mXOffset;
        float mYBaseOffset;


        HashMap<String,Bitmap> mWeatherIcons;
        static final String KEY_WEATHER_CLEAR = "weather_clear";
        static final String KEY_WEATHER_CLOUDY = "weather_cloudy";
        static final String KEY_WEATHER_FOG = "weather_fog";
        static final String KEY_WEATHER_LIGHT_CLOUDS = "weather_light_clouds";
        static final String KEY_WEATHER_LIGHT_RAIN = "weather_light_rain";
        static final String KEY_WEATHER_RAIN = "weather_rain";
        static final String KEY_WEATHER_SNOW = "weather_snow";
        static final String KEY_WEATHER_STORM = "weather_storm";

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Resources resources;
            int textColor;

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            resources = SunshineWatchFace.this.getResources();
            mYBaseOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            textColor = resources.getColor(R.color.digital_text);
            mHoursMinutesTextPaint = createTextPaint(textColor);
            mSecondsTextPaint = createTextPaint(textColor);
            mDateTextPaint = createTextPaint(textColor);
            mHighTempTextPaint = createTextPaint(textColor);
            mHighTempTextPaint.setTypeface(BOLD_TYPEFACE);
            mLowTempTextPaint = createTextPaint(textColor);
            mLowTempTextPaint.setAlpha(200);

            mCalendar = Calendar.getInstance();

            mWeatherIcons = new HashMap<>();
            mWeatherIcons.put(KEY_WEATHER_CLEAR, BitmapFactory.decodeResource(resources, R.drawable.ic_clear));
            mWeatherIcons.put(KEY_WEATHER_CLOUDY, BitmapFactory.decodeResource(resources, R.drawable.ic_cloudy));
            mWeatherIcons.put(KEY_WEATHER_FOG, BitmapFactory.decodeResource(resources, R.drawable.ic_fog));
            mWeatherIcons.put(KEY_WEATHER_LIGHT_CLOUDS, BitmapFactory.decodeResource(resources, R.drawable.ic_light_clouds));
            mWeatherIcons.put(KEY_WEATHER_LIGHT_RAIN, BitmapFactory.decodeResource(resources, R.drawable.ic_light_rain));
            mWeatherIcons.put(KEY_WEATHER_RAIN, BitmapFactory.decodeResource(resources, R.drawable.ic_rain));
            mWeatherIcons.put(KEY_WEATHER_SNOW, BitmapFactory.decodeResource(resources, R.drawable.ic_snow));
            mWeatherIcons.put(KEY_WEATHER_STORM, BitmapFactory.decodeResource(resources, R.drawable.ic_storm));
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float defaultTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mHoursMinutesTextPaint.setTextSize(defaultTextSize);
            mSecondsTextPaint.setTextSize(defaultTextSize*0.5f);
            mDateTextPaint.setTextSize(defaultTextSize*0.4f);
            mHighTempTextPaint.setTextSize(defaultTextSize*0.7f);
            mLowTempTextPaint.setTextSize(defaultTextSize*0.7f);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHoursMinutesTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            Bitmap currentWeatherIcon;
            String hoursMinutesText;
            String dateText;
            long now;
            int hours;
            int minutes;
            int seconds;

            float hoursMinutesWidth;
            float hoursMinutesXOffset;
            float secondsXOffset;
            float secondsMarginLeft = 2;
            float timeYOffset;
            float dateWidth;
            float dateXOffset;
            float dateYOffset;
            float dateMarginTop = 5;
            float separatorWidth;
            float separatorXOffset;
            float separatorYOffset;
            float separatorMarginTop = 25;
            float separatorMarginBottom = 10;
            float weatherSectionWidth;
            float weatherIconWidth;
            float weatherIconHeight;
            float weatherIconMarginRight = 25;
            float highTempMarginRight = 10;
            float highTempWidth;
            float highTempHeight;
            float lowTempWidth;
            float weatherIconXOffset;
            float weatherIconYOffset;
            float highTempXOffset;
            float lowTempXOffset;
            float tempYOffset;

            Rect dateBounds;
            Rect highTempBounds;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM SS in interactive mode.
            now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            hours = mCalendar.get(Calendar.HOUR);
            if(hours == 0){
                hours = 12;
            }
            minutes = mCalendar.get(Calendar.MINUTE);
            seconds = mCalendar.get(Calendar.SECOND);

            hoursMinutesText = String.format("%d:%02d", hours, minutes);
            hoursMinutesWidth = mHoursMinutesTextPaint.measureText(hoursMinutesText);
            hoursMinutesXOffset = bounds.centerX() - hoursMinutesWidth/2;
            secondsXOffset = hoursMinutesXOffset + hoursMinutesWidth + secondsMarginLeft;
            timeYOffset = mYBaseOffset;

            mHoursMinutesTextPaint.setAntiAlias(!mAmbient);
            mDateTextPaint.setAntiAlias(!mAmbient);

            canvas.drawText(hoursMinutesText, hoursMinutesXOffset, timeYOffset, mHoursMinutesTextPaint);
            if(!mAmbient){
                canvas.drawText(String.format("%02d",seconds), secondsXOffset, timeYOffset, mSecondsTextPaint);
            }

            //Draw date
            dateText = DateUtils.formatDateTime(SunshineWatchFace.this, mCalendar.getTimeInMillis(),
                            DateUtils.FORMAT_SHOW_WEEKDAY |
                            DateUtils.FORMAT_SHOW_DATE |
                            DateUtils.FORMAT_SHOW_YEAR |
                            DateUtils.FORMAT_ABBREV_ALL);

            dateWidth = mDateTextPaint.measureText(dateText);
            dateBounds = new Rect();
            mDateTextPaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            dateXOffset = bounds.centerX() - dateWidth/2;
            dateYOffset = mYBaseOffset + dateBounds.height() + dateMarginTop;
            canvas.drawText(dateText, dateXOffset, dateYOffset, mDateTextPaint);

            currentWeatherIcon = mWeatherIcons.get(mCurrentWeather);

            //Draw weather info in interactive mode
            if(!mAmbient && currentWeatherIcon != null) {
                String highTemp;
                String lowTemp;

                separatorWidth = bounds.width()/3;
                separatorXOffset = bounds.centerX() - separatorWidth/2;
                separatorYOffset = dateYOffset + separatorMarginTop;
                canvas.drawLine(separatorXOffset, separatorYOffset, separatorXOffset + separatorWidth, separatorYOffset, mSecondsTextPaint);

                highTemp = mHighTemp + DEGREE_CHARACTER;
                lowTemp = mLowTemp + DEGREE_CHARACTER;

                highTempBounds = new Rect();
                mHighTempTextPaint.getTextBounds(highTemp, 0, highTemp.length(), highTempBounds);
                highTempHeight = highTempBounds.height();

                weatherIconWidth = currentWeatherIcon.getWidth();
                weatherIconHeight = currentWeatherIcon.getHeight();
                highTempWidth = mHighTempTextPaint.measureText(highTemp);
                lowTempWidth = mLowTempTextPaint.measureText(lowTemp);
                weatherSectionWidth = weatherIconWidth + weatherIconMarginRight + highTempWidth + highTempMarginRight + lowTempWidth;
                weatherIconXOffset = bounds.centerX() - weatherSectionWidth/2;
                highTempXOffset = weatherIconXOffset + weatherIconWidth + weatherIconMarginRight;
                lowTempXOffset = highTempXOffset + highTempWidth + highTempMarginRight;
                weatherIconYOffset = separatorYOffset + separatorMarginBottom;
                //assume the highTemp and lowTemp height are relatively the same so we use highTempHeight to calculate the Y offset
                //for both temperature values
                tempYOffset = weatherIconYOffset + weatherIconHeight/2 + highTempHeight/2;

                canvas.drawBitmap(currentWeatherIcon, weatherIconXOffset, weatherIconYOffset, mBackgroundPaint);
                canvas.drawText(highTemp, highTempXOffset, tempYOffset, mHighTempTextPaint);
                canvas.drawText(lowTemp, lowTempXOffset, tempYOffset, mLowTempTextPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
