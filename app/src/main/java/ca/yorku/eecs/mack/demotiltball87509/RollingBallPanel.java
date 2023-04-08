package ca.yorku.eecs.mack.demotiltball87509;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Locale;

public class RollingBallPanel extends View
{
    final static float DEGREES_TO_RADIANS = 0.0174532925f;

    // the ball diameter will be min(width, height) / this_value
    final static float BALL_DIAMETER_ADJUST_FACTOR = 30;

    final static int DEFAULT_LABEL_TEXT_SIZE = 20; // tweak as necessary
    final static int DEFAULT_STATS_TEXT_SIZE = 10;
    final static int DEFAULT_GAP = 7; // between lines of text
    final static int DEFAULT_OFFSET = 10; // from bottom of display

    final static int MODE_NONE = 0;
    final static int PATH_TYPE_SQUARE = 1;
    final static int PATH_TYPE_CIRCLE = 2;

    final static float PATH_WIDTH_NARROW = 2f; // ... x ball diameter
    final static float PATH_WIDTH_MEDIUM = 4f; // ... x ball diameter
    final static float PATH_WIDTH_WIDE = 8f; // ... x ball diameter

    float radiusOuter, radiusInner;

    Bitmap ball, decodedBallBitmap;
    int ballDiameter;

    float dT; // time since last sensor event (seconds)

    float width, height, pixelDensity;
    int labelTextSize, statsTextSize, gap, offset;

    RectF innerRectangle, outerRectangle, innerShadowRectangle, outerShadowRectangle, ballNow;
    boolean touchFlag;
    Vibrator vib;
    int wallHits;

    float xBall, yBall; // top-left of the ball (for painting)
    float xBallCenter, yBallCenter; // center of the ball

    float pitch, roll;
    float tiltAngle, tiltMagnitude;

    // parameters from Setup dialog
    String orderOfControl;
    float gain, pathWidth;
    int pathType;

    float velocity; // in pixels/second (velocity = tiltMagnitude * tiltVelocityGain
    float dBall; // the amount to move the ball (in pixels): dBall = dT * velocity
    float xCenter, yCenter; // the center of the screen
    long now, lastT;
    Paint statsPaint, labelPaint, linePaint, fillPaint, backgroundPaint;
    float[] updateY;

    //Doing lap should be true if user has started a lap and has not yet fell out of bounds
    //Three checkpoints around the circle in counter-clock wise direction
    //Idea is you must complete them in order to ensure that the user is doing the correct direction in their lap
    Boolean doingLap, firstCheck, secondCheck, thirdCheck, inPath, checkPath;

    //Added in order to check laps
    int laps;
    int lapsDone;

    CountDownTimer lapTimer, inPathTimer;
    int totalLapTime, totalPathTIme, currentLapTime, currentPathTime;


    public RollingBallPanel(Context contextArg)
    {
        super(contextArg);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs)
    {
        super(contextArg, attrs);
        initialize(contextArg);
    }

    public RollingBallPanel(Context contextArg, AttributeSet attrs, int defStyle)
    {
        super(contextArg, attrs, defStyle);
        initialize(contextArg);
    }

    // things that can be initialized from within this View
    private void initialize(Context c)
    {
        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(0xffccbbbb);
        fillPaint.setStyle(Paint.Style.FILL);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.LTGRAY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(DEFAULT_LABEL_TEXT_SIZE);
        labelPaint.setAntiAlias(true);

        statsPaint = new Paint();
        statsPaint.setAntiAlias(true);
        statsPaint.setTextSize(DEFAULT_STATS_TEXT_SIZE);

        // NOTE: we'll create the actual bitmap in onWindowFocusChanged
        decodedBallBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ball);

        lastT = System.nanoTime();
        this.setBackgroundColor(Color.LTGRAY);
        touchFlag = false;
        outerRectangle = new RectF();
        innerRectangle = new RectF();
        innerShadowRectangle = new RectF();
        outerShadowRectangle = new RectF();
        ballNow = new RectF();
        wallHits = 0;

        vib = (Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);

        //Rest Lap checks
        doingLap = false;
        firstCheck = false;
        secondCheck = false;
        thirdCheck = false;
        inPath = false;

        lapsDone = 0;

        currentLapTime = 0;
        currentPathTime = 0;
        totalPathTIme = 0;
        totalLapTime = 0;

        //Setup timers
        lapTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                    currentLapTime++;

                    long millis = currentLapTime;
                    int seconds = (int) (millis / 60);
                    int minutes = seconds / 60;
                    String timeCounts = String.format("%d:%02d:%02d", minutes, seconds, millis);
                    Log.i("COUNT", timeCounts);
            }
            @Override
            public void onFinish() {

            }
        };

        inPathTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                currentPathTime++;

                long millis = currentPathTime;
                int seconds = (int) (millis / 60);
                int minutes = seconds / 60;
                String timeCounts = String.format("PATH: %d:%02d:%02d", minutes, seconds, millis);
                Log.i("COUNT", timeCounts);
            }

            @Override
            public void onFinish() {

            }
        };
    }

    /**
     * Called when the window hosting this view gains or looses focus.  Here we initialize things that depend on the
     * view's width and height.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        if (!hasFocus)
            return;

        width = this.getWidth();
        height = this.getHeight();

        // the ball diameter is nominally 1/30th the smaller of the view's width or height
        ballDiameter = width < height ? (int)(width / BALL_DIAMETER_ADJUST_FACTOR)
                : (int)(height / BALL_DIAMETER_ADJUST_FACTOR);

        // now that we know the ball's diameter, get a bitmap for the ball
        ball = Bitmap.createScaledBitmap(decodedBallBitmap, ballDiameter, ballDiameter, true);

        // center of the view
        xCenter = width / 2f;
        yCenter = height / 2f;

        // top-left corner of the ball
        xBall = xCenter;
        yBall = yCenter;

        // center of the ball
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        // configure outer rectangle of the path
        radiusOuter = width < height ? 0.40f * width : 0.40f * height;
        outerRectangle.left = xCenter - radiusOuter;
        outerRectangle.top = yCenter - radiusOuter;
        outerRectangle.right = xCenter + radiusOuter;
        outerRectangle.bottom = yCenter + radiusOuter;

        // configure inner rectangle of the path
        // NOTE: medium path width is 4 x ball diameter
        radiusInner = radiusOuter - pathWidth * ballDiameter;
        innerRectangle.left = xCenter - radiusInner;
        innerRectangle.top = yCenter - radiusInner;
        innerRectangle.right = xCenter + radiusInner;
        innerRectangle.bottom = yCenter + radiusInner;

        // configure outer shadow rectangle (needed to determine wall hits)
        // NOTE: line thickness (aka stroke width) is 2
        outerShadowRectangle.left = outerRectangle.left + ballDiameter - 2f;
        outerShadowRectangle.top = outerRectangle.top + ballDiameter - 2f;
        outerShadowRectangle.right = outerRectangle.right - ballDiameter + 2f;
        outerShadowRectangle.bottom = outerRectangle.bottom - ballDiameter + 2f;

        // configure inner shadow rectangle (needed to determine wall hits)
        innerShadowRectangle.left = innerRectangle.left + ballDiameter - 2f;
        innerShadowRectangle.top = innerRectangle.top + ballDiameter - 2f;
        innerShadowRectangle.right = innerRectangle.right - ballDiameter + 2f;
        innerShadowRectangle.bottom = innerRectangle.bottom - ballDiameter + 2f;

        // initialize a few things (e.g., paint and text size) that depend on the device's pixel density
        pixelDensity = this.getResources().getDisplayMetrics().density;
        labelTextSize = (int)(DEFAULT_LABEL_TEXT_SIZE * pixelDensity + 0.5f);
        labelPaint.setTextSize(labelTextSize);

        statsTextSize = (int)(DEFAULT_STATS_TEXT_SIZE * pixelDensity + 0.5f);
        statsPaint.setTextSize(statsTextSize);

        gap = (int)(DEFAULT_GAP * pixelDensity + 0.5f);
        offset = (int)(DEFAULT_OFFSET * pixelDensity + 0.5f);

        // compute y offsets for painting stats (bottom-left of display)
        updateY = new float[7]; // up to 6 lines of stats will appear
        for (int i = 0; i < updateY.length; ++i)
            updateY[i] = height - offset - i * (statsTextSize + gap);
    }

    /*
     * Do the heavy lifting here! Update the ball position based on the tilt angle, tilt
     * magnitude, order of control, etc.
     */
    public void updateBallPosition(float pitchArg, float rollArg, float tiltAngleArg, float tiltMagnitudeArg)
    {
        pitch = pitchArg; // for information only (see onDraw)
        roll = rollArg; // for information only (see onDraw)
        tiltAngle = tiltAngleArg;
        tiltMagnitude = tiltMagnitudeArg;

        // get current time and delta since last onDraw
        now = System.nanoTime();
        dT = (now - lastT) / 1000000000f; // seconds
        lastT = now;

        // don't allow tiltMagnitude to exceed 45 degrees
        final float MAX_MAGNITUDE = 45f;
        tiltMagnitude = tiltMagnitude > MAX_MAGNITUDE ? MAX_MAGNITUDE : tiltMagnitude;

        // This is the only code that distinguishes velocity-control from position-control
        if (orderOfControl.equals("Velocity")) // velocity control
        {
            // compute ball velocity (depends on the tilt of the device and the gain setting)
            velocity = tiltMagnitude * gain;

            // compute how far the ball should move (depends on the velocity and the elapsed time since last update)
            dBall = dT * velocity; // make the ball move this amount (pixels)

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            xBall += dx;
            yBall += dy;

        } else
        // position control
        {
            // compute how far the ball should move (depends on the tilt of the device and the gain setting)
            dBall = tiltMagnitude * gain;

            // compute the ball's new coordinates (depends on the angle of the device and dBall, as just computed)
            float dx = (float)Math.sin(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            float dy = -(float)Math.cos(tiltAngle * DEGREES_TO_RADIANS) * dBall;
            xBall = xCenter + dx;
            yBall = yCenter + dy;
        }

        // make an adjustment, if necessary, to keep the ball visible (also, restore if NaN)
        if (Float.isNaN(xBall) || xBall < 0)
            xBall = 0;
        else if (xBall > width - ballDiameter)
            xBall = width - ballDiameter;
        if (Float.isNaN(yBall) || yBall < 0)
            yBall = 0;
        else if (yBall > height - ballDiameter)
            yBall = height - ballDiameter;

        // oh yea, don't forget to update the coordinate of the center of the ball (needed to determine wall  hits)
        xBallCenter = xBall + ballDiameter / 2f;
        yBallCenter = yBall + ballDiameter / 2f;

        // if ball touches wall, vibrate and increment wallHits count
        // NOTE: We also use a boolean touchFlag so we only vibrate on the first touch
        if (ballTouchingLine() && !touchFlag)
        {
            touchFlag = true; // the ball has *just* touched the line: set the touchFlag

            //Modified with conditional so that there is only a vibration if the ball travels
            //OUT of the path
            if(!inPath){
                inPath = true;

                //start path time if in path
                if(doingLap){
                    inPathTimer.start();
                }
            }else{
                vib.vibrate(50); // 50 ms vibrotactile pulse
                ++wallHits;

                //Take out of path
                checkPath = true;

                //Stop timer if doing a lap
                if(doingLap){
                    inPathTimer.cancel();
                    totalPathTIme += currentPathTime;
                    currentPathTime = 0;
                }
            }

        } else if (!ballTouchingLine() && touchFlag) {
            touchFlag = false; // the ball is no longer touching the line: clear the touchFlag
        }


        //Double check if ball actually left path
        if(pathType == PATH_TYPE_SQUARE){
            if(outerRectangle.contains(xBallCenter, yBallCenter) && !innerRectangle.contains(xBallCenter, yBallCenter)){
                if(!inPath){
                    inPath = true;
                    Log.i("DEBUG", "Path Correction");
                }
            }else{
                if(inPath){
                    inPath = false;
                    Log.i("DEBUG", "Path Correction");
                }
            }

        } else if (pathType == PATH_TYPE_CIRCLE) {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));


            if(!inPath){
                //Check if ball is actually in the circle, radius is in between the two circles
                // (ballDiameter / 2) is add because we don't want to count the ball is in if half of it is sticking out
                if ((ballDistance < radiusOuter - (ballDiameter / 2)) && (ballDistance > radiusInner + (ballDiameter / 2))){
                    Log.i("DEBUG", "Path Correction");
                    inPath = true;
                }
            }else {
                if (!((ballDistance < radiusOuter - (ballDiameter / 2)) && (ballDistance > radiusInner + (ballDiameter / 2)))) {
                    Log.i("DEBUG", "Path Correction");
                    inPath = false;

                }
            }
        }


        //Check if starting a new lap
        if(ballTouchingLapLine() && !doingLap){
            Log.i("DEBUG", "Started Lap!!!");
            doingLap = true;

            //Start the timer for this lap
            lapTimer.start();

            //Start Path timer
            inPathTimer.start();

            vib.vibrate(50);
        }

        //Check if a checkpoint status needs to be updated
        if(doingLap ){
            ballCrossingCheck();
        }

        //Checking if a lap was completed
        if(ballTouchingLapLine() && firstCheck && secondCheck){
            vib.vibrate(50);
            Log.i("DEBUG", "Finished a lap");
            lapsDone++;
            Log.i("DEBUG", "Laps: " + laps + ", Laps Done: " + lapsDone);

            if(lapsDone >= laps){
                doingLap = false;
                firstCheck = false;
                secondCheck = false;
                inPath = false;

                //get the time totals
                inPathTimer.cancel();
                totalPathTIme += currentPathTime;
                currentPathTime = 0;

                lapTimer.cancel();
                totalLapTime += currentLapTime;
                currentLapTime = 0;

                finished();
            }else{
                //add to stats
                firstCheck = false;
                secondCheck = false;

                //Log
                Log.i("COUNT", "FINISHED A LAP");
                Log.i("COUNT","Total Lap Time: " + totalLapTime);
            }
        }
        invalidate(); // force onDraw to redraw the screen with the ball in its new position
    }

    protected void onDraw(Canvas canvas)
    {
        // center of the view
        xCenter = width / 2f;
        yCenter = height / 2f;

        // check if view is ready for drawing
        if (updateY == null)
            return;

        // draw the paths
        if (pathType == PATH_TYPE_SQUARE)
        {
            // draw fills
            canvas.drawRect(outerRectangle, fillPaint);
            canvas.drawRect(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawRect(outerRectangle, linePaint);
            canvas.drawRect(innerRectangle, linePaint);

            //draw lap line
            canvas.drawLine(outerRectangle.left, yCenter, innerRectangle.left, yCenter, linePaint);
        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            // draw fills
            canvas.drawOval(outerRectangle, fillPaint);
            canvas.drawOval(innerRectangle, backgroundPaint);

            // draw lines
            canvas.drawOval(outerRectangle, linePaint);
            canvas.drawOval(innerRectangle, linePaint);

            //draw lap line
            canvas.drawLine(outerRectangle.left, yCenter, innerRectangle.left, yCenter, linePaint);
        }

        // draw label
        canvas.drawText("Demo_TiltBall", 6f, labelTextSize, labelPaint);

        // draw stats (pitch, roll, tilt angle, tilt magnitude)
        if (pathType == PATH_TYPE_SQUARE || pathType == PATH_TYPE_CIRCLE)
        {
            canvas.drawText("Wall hits = " + wallHits, 6f, updateY[6], statsPaint);
            canvas.drawText("Laps =  " + lapsDone + "/" + laps, 6f,updateY[5], statsPaint);
            canvas.drawText("-----------------", 6f, updateY[4], statsPaint);
        }
        canvas.drawText(String.format(Locale.CANADA, "Tablet pitch (degrees) = %.2f", pitch), 6f, updateY[3],
                statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Tablet roll (degrees) = %.2f", roll), 6f, updateY[2], statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Ball x = %.2f", xBallCenter), 6f, updateY[1], statsPaint);
        canvas.drawText(String.format(Locale.CANADA, "Ball y = %.2f", yBallCenter), 6f, updateY[0], statsPaint);

        // create and draw triangles
        // use a Path object to store the 3 line segments
        // use .offset to draw in many locations
        // note: this triangle is not centered at 0,0
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.RED);
        Path path = new Path();
        path.moveTo(0, 40);
        path.lineTo(20, 0);
        path.lineTo(-20, 0);
        path.close();
        path.offset(outerRectangle.left - 40f, yCenter - 10f);
        canvas.drawPath(path, paint);

        // draw the ball in its new location
        canvas.drawBitmap(ball, xBall, yBall, null);
    } // end onDraw

    /*
     * Configure the rolling ball panel according to setup parameters
     */
    public void configure(String pathMode, String pathWidthArg, int gainArg, String orderOfControlArg, int laps)
    {
        // square vs. circle
        if (pathMode.equals("Square"))
            pathType = PATH_TYPE_SQUARE;
        else if (pathMode.equals("Circle"))
            pathType = PATH_TYPE_CIRCLE;
        else
            pathType = MODE_NONE;

        // narrow vs. medium vs. wide
        if (pathWidthArg.equals("Narrow"))
            pathWidth = PATH_WIDTH_NARROW;
        else if (pathWidthArg.equals("Wide"))
            pathWidth = PATH_WIDTH_WIDE;
        else
            pathWidth = PATH_WIDTH_MEDIUM;

        gain = gainArg;
        orderOfControl = orderOfControlArg;
        this.laps = laps;
        invalidate();
    }

    //Returns true if ball is touch the lap line
    public boolean ballTouchingLapLine(){
        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;

        //RectF lineHitBottom = new RectF();
        RectF lapLine = new RectF(outerRectangle.left, yCenter, innerRectangle.left, yCenter);

        if (RectF.intersects(ballNow, lapLine)) {
            return true;
        }else {
            return false;
        }
    }

    //Checks what checkpoint the ball is crossing if any
    //returns 0 if nothing crossed
    public void ballCrossingCheck(){
        ballNow.left = xBall;
        ballNow.top = yBall;
        ballNow.right = xBall + ballDiameter;
        ballNow.bottom = yBall + ballDiameter;

        //Create three check points
        RectF newLine = new RectF(xCenter, yCenter, xCenter, yCenter * 2);

        RectF secondLine = new RectF(xCenter, 0, xCenter, yCenter);

        if(!firstCheck){
            if(RectF.intersects(ballNow, newLine)){
                firstCheck = true;
                Log.i("DEBUG", "FIRST CHECK PASSED");
            }

        }else if(!secondCheck){
            if (RectF.intersects(ballNow, secondLine)) {
                secondCheck = true;
                Log.i("DEBUG", "Second mark passed");
            }
        }
    }



    // returns true if the ball is touching (i.e., overlapping) the line of the inner or outer path border
    public boolean ballTouchingLine()
    {
        if (pathType == PATH_TYPE_SQUARE)
        {
            ballNow.left = xBall;
            ballNow.top = yBall;
            ballNow.right = xBall + ballDiameter;
            ballNow.bottom = yBall + ballDiameter;

            if (RectF.intersects(ballNow, outerRectangle) && !RectF.intersects(ballNow, outerShadowRectangle))
                return true; // touching outside rectangular border

            if (RectF.intersects(ballNow, innerRectangle) && !RectF.intersects(ballNow, innerShadowRectangle))
                return true; // touching inside rectangular border

        } else if (pathType == PATH_TYPE_CIRCLE)
        {
            final float ballDistance = (float)Math.sqrt((xBallCenter - xCenter) * (xBallCenter - xCenter)
                    + (yBallCenter - yCenter) * (yBallCenter - yCenter));

            if (Math.abs(ballDistance - radiusOuter) < (ballDiameter / 2f))
                return true; // touching outer circular border

            if (Math.abs(ballDistance - radiusInner) < (ballDiameter / 2f))
                return true; // touching inner circular border
        }
        return false;
    }

    //This method should be called when the user has finished all of their laps
    //We then start the results activity
    public void finished(){
        DemoTiltBall87509Activity dTBA = (DemoTiltBall87509Activity) getActivity();
        Log.i("DEBUG", "FINISHED WITH lap time: " + totalLapTime + ", Path time: " + totalPathTIme);
        dTBA.finished(lapsDone, wallHits, totalLapTime, totalPathTIme);
    }

    //Added this snippet in order to get access to the activity this view is hosted from
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }
}
