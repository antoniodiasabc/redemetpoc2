package com.pocsigmet.util;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

@Component
public class ImageTransparencyProcessor {
    
    public void removeBackground(String imagePath) {
        Mat image = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_UNCHANGED);
        Mat result = new Mat();
        
        Imgproc.cvtColor(image, result, Imgproc.COLOR_BGR2BGRA);
        
        Mat mask = new Mat();
        Core.inRange(result, new Scalar(0, 0, 0, 0), new Scalar(50, 50, 50, 255), mask);
        
        result.setTo(new Scalar(0, 0, 0, 0), mask);
        
        Imgcodecs.imwrite(imagePath, result);
    }
}
