package com.groupon.seleniumgridextras.utilities;

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.groupon.seleniumgridextras.VideoHttpExecutor;
import com.groupon.seleniumgridextras.config.DefaultConfig;
import com.groupon.seleniumgridextras.config.RuntimeConfig;
import com.groupon.seleniumgridextras.utilities.threads.video.VideoRecorderCallable;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


public class HttpUtility {

    private static Logger logger = Logger.getLogger(HttpUtility.class);


    public static String getRequestAsString(URI uri) throws IOException, URISyntaxException {
        return getRequestAsString(uri.toURL());
    }

    public static String getRequestAsString(URL url) throws IOException {
        return getRequestAsString(url, RuntimeConfig.getConfig() != null ?
                RuntimeConfig.getConfig().getHttpRequestTimeout() :
                DefaultConfig.HTTP_REQUEST_TIMEOUT);
    }

    public static String getRequestAsString(URL url, int timeout) throws IOException {
        HttpURLConnection conn = getRequest(url, timeout);

        if (conn.getResponseCode() == 200) {
            return StreamUtility.inputStreamToString(conn.getInputStream());
        } else {
            return "";
        }
    }

    public static HttpURLConnection getRequest(URI uri) throws IOException {
        return getRequest(uri.toURL());
    }

    public static HttpURLConnection getRequest(URL url) throws IOException {
        return getRequest(url, RuntimeConfig.getConfig() != null ?
                RuntimeConfig.getConfig().getHttpRequestTimeout() :
                DefaultConfig.HTTP_REQUEST_TIMEOUT);
    }

    public static HttpURLConnection getRequest(URL url, int timeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        logger.debug("Response code is " + conn.getResponseCode());
        return conn;
    }

    public static File downloadVideoFromUri(URI uri, String session) throws IOException {
        //Don't modify this without running the comment out tests!

        File destinationDir;
        File testJSONDir;
        if (RuntimeConfig.getConfig() != null) {
            destinationDir = RuntimeConfig.getConfig().getVideoRecording().getOutputDir();
            testJSONDir = RuntimeConfig.getConfig().getVideoRecording().getTestJSONDir();
        } else {
            destinationDir = new File(DefaultConfig.VIDEO_OUTPUT_DIRECTORY);
            testJSONDir = new File(DefaultConfig.TEST_JSON_DIR);
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdir();
        }
        
        if (!testJSONDir.exists()) {
        	testJSONDir.mkdir();
        }

        
        // Delete old movies
        VideoRecorderCallable.deleteOldMovies(destinationDir);
        VideoRecorderCallable.deleteOldTestJSONFiles(testJSONDir);

        File hubDestinationFile = new File(
                destinationDir.getAbsolutePath(),
                uri.getRawPath().replaceAll(
                        VideoHttpExecutor.GET_VIDEO_FILE_ENDPOINT,
                        "").replaceAll(
                        "/",
                        ""));

        File destFile = HttpUtility.getTestSessionDownload(hubDestinationFile, testJSONDir, session);
        
        try {
            FileUtils.copyURLToFile(uri.toURL(), destFile);
        } catch (Exception e) {
            logger.error(
                    String.format(
                            "Exception happened while downloading video. file: %s, dest dir: %s, source url: %s",
                            destFile.getAbsolutePath(),
                            destinationDir.getAbsolutePath(),
                            uri.toString()),
                    e);
            return null;
        }

        return destFile;
    }

    public static int checkIfUrlStatusCode(URL u) {

        HttpURLConnection huc = null;
        try {

            huc = (HttpURLConnection) u.openConnection();
            huc.setRequestMethod("GET");
            huc.setInstanceFollowRedirects(true);
            huc.connect();
//            OutputStream os = huc.getOutputStream();
            return huc.getResponseCode();
        } catch (IOException e) {
            String message = String.format("URL: %s, \n %s", u, Throwables.getStackTraceAsString(e));
            System.out.println(message);
            logger.warn(message);
        }

        return -1;
    }
    
    public static TestInfo testSessionDownloadInfo(File testSessionJSON) throws FileNotFoundException {
    	Gson gson = new Gson();
    	BufferedReader br = new BufferedReader(
    			new FileReader(testSessionJSON));

		TestInfo testInfo = gson.fromJson(br, TestInfo.class);
		return testInfo;
    }
    
    public static File getTestSessionDownload(File hubDestinationFile, File testJSONDir, String session) throws IOException {
//    	Gson gson = new Gson();
    	File testSessionDestinationFile = null;
    	try {
    		String testJSONFile = String.format("%s.json", session);    		
    		File testSessionJSON = new File(testJSONDir, testJSONFile);
    		
    		// If no matching session file exists then copy to default hub location
    		if (!doesTestSessionFileExist(testSessionJSON, session)) {
    			return hubDestinationFile;
    		}
    		
    		logger.info(String.format("Try to copy video file for session %s", session));
    		 
    		TestInfo testInfo = testSessionDownloadInfo(testSessionJSON);
//    		BufferedReader br = new BufferedReader(
//    			new FileReader(jsonLocation));
//
//    		TestInfo testInfo = gson.fromJson(br, TestInfo.class);
    		
    		File outputDir = new File(testInfo.OutputDir);
    		if (!outputDir.exists()) {
    			outputDir.mkdir();
            }
    		
    		VideoRecorderCallable.deleteOldMovies(outputDir);
    		
    		testSessionDestinationFile = new File(outputDir, testInfo.OutputFile);
            logger.info(String.format("Found output destination for video %s", 
            		testSessionDestinationFile.getAbsolutePath()));

    	} catch (IOException e) {
    		String error = String.format("IOError when trying to find test session video output location, %s\n%s",
                    e.getMessage(),
                    Throwables.getStackTraceAsString(e));

            logger.warn(error);
    	} catch (Exception e) { //This catch all is in case something goes wrong in parsing or something so it's not lost
	        String error = String.format("Something went CATASTROPHICALLY wrong when trying to find test session video output location, %s\n%s",
	                e.getMessage(),
	                Throwables.getStackTraceAsString(e));
	
	        logger.error(error);
	    }
    	
    	if (testSessionDestinationFile == null) {
    		return hubDestinationFile;
    	}
    	
    	return testSessionDestinationFile;
    }
    
    public static boolean doesTestSessionFileExist(File file, String session) {
    	logger.info(String.format("Check if test's json file exists: %s",
                file.getAbsolutePath()));
    	
    	if (file.exists()) {
            logger.info(String.format("Found test's json file: %s",
                    file.getAbsolutePath()));
            return true;
        } else {
            logger.info(String.format(
                    "Test does not have a json file for this session %s. Don't copy video file.",
                    session));
            return false;
        }
    }
}
