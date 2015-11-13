package com.groupon.seleniumgridextras.utilities.threads.video;

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.groupon.seleniumgridextras.config.RuntimeConfig;
import com.groupon.seleniumgridextras.tasks.config.TaskDescriptions;
import com.groupon.seleniumgridextras.utilities.HttpUtility;
import com.groupon.seleniumgridextras.utilities.json.JsonCodec;
import com.groupon.seleniumgridextras.utilities.json.JsonParserWrapper;
//import com.medmined.seleniumgridextras.testinfo.TestInfo;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;


public class VideoDownloaderCallable implements Callable {

    private static Logger logger = Logger.getLogger(VideoDownloaderCallable.class);
    private final String session;
    private final String host;
    private final URI uri;
    private final int ATTEMPTS_TO_DOWNLOAD = 5;
    private final int TIME_TO_WAIT_BETWEEN_ATTEMPTS = 30000;

    public VideoDownloaderCallable(String session, String host) {
        logger.info(String.format("New instance for session: %s host: %s", session, host));
        this.session = session;
        this.host = host;
        this.uri = buildVideoStatusUri();

    }

    @Override
    public String call() throws Exception {

        if (this.uri == null) {
            logger.warn(
                    String.format(
                            "Video status URI was not set for session %s host %s, will not attempt to download video",
                            this.session,
                            this.host));

            return null;
        }

        Map response = null;
        for (int i = 0; i < ATTEMPTS_TO_DOWNLOAD; i++) {
            logger.info(String.format(
                    "Attempt %s of %s to download test video for session %s from host %s",
                    i,
                    ATTEMPTS_TO_DOWNLOAD,
                    this.session,
                    this.host));
            try {
                response = getVideoStatusFromNode();

                if (response != null) {
                    if (attemptToDownloadVideo(response)) {
                        return String.format("Successfully downloaded video for session %s from host %s", this.session, this.host);
                    }
                } else {
                    logger.info(String.format(
                            "Download of video %s from host %s was not successful, will try again after %s ms",
                            this.session,
                            this.host,
                            TIME_TO_WAIT_BETWEEN_ATTEMPTS));
                    Thread.sleep(TIME_TO_WAIT_BETWEEN_ATTEMPTS);
                }

            } catch (Exception e) {
                logger.error(
                        String.format(
                                "Something unexpected happened response %s\ntrace: %s",
                                (response != null ? response.toString() : "(No response available)"),
                                Throwables.getStackTraceAsString(e))

                );
            }
        }


        String error =String.format(
                "Failed to download video for session %s from host %s after %s attempts",
                this.session,
                this.host,
                ATTEMPTS_TO_DOWNLOAD);
        logger.warn(error);
        return error;  //To change body of implemented methods use File | Settings | File Templates.
    }

    protected boolean attemptToDownloadVideo(Map info) throws URISyntaxException, NullPointerException {
        if ((!info.containsKey(JsonCodec.Video.CURRENT_VIDEOS) || (!info.containsKey(JsonCodec.Video.AVAILABLE_VIDEOS)))) {
            logger.warn(String.format("Video info for session: %s host: %s, it did not contain key %s or %s,\n%s",
                    this.session,
                    this.host,
                    JsonCodec.Video.CURRENT_VIDEOS,
                    JsonCodec.Video.AVAILABLE_VIDEOS,
                    info));
            return false;
        }

        List currentVideos = (List) info.get(JsonCodec.Video.CURRENT_VIDEOS);
        if (currentVideos.contains(this.session)) {
            logger.info(
                    String.format(
                            "Session %s on host %s is still recording or rendering",
                            this.session,
                            this.host));
            return false;
        }

        Map availableVideos = (Map) info.get(JsonCodec.Video.AVAILABLE_VIDEOS);
        Map desiredVideo;
        if (!availableVideos.containsKey(this.session)) {
            logger.warn(
                    String.format(
                            "Video for session %s does not exist on host %s",
                            this.session,
                            this.host));
            return false;
        } else {
            desiredVideo = (Map) availableVideos.get(this.session);
        }


        String url = (String) desiredVideo.get(JsonCodec.Video.VIDEO_DOWNLOAD_URL);

        File downloadedFile = HttpUtility.downloadVideoFromUri(new URI(url));

        if (downloadedFile.exists()) {
            logger.info(String.format("Successfully downloaded video for session %s, from host %s, to directory on hub %s",
                    this.session,
                    this.host,
                    downloadedFile.getAbsolutePath()));

            // Copy to required destination
            /* IMPORTANT:
             * This is where we changed to read in from file or sql to copy video file
             * to the output directory we want it to go to. An to rename file with
             * testname_sessionid.mp4
             * 
             */
            try {
				CopyVideoFile(this.session);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            return true;
        } else {
            logger.info(String.format(
                    "Download of video for session %s from host %s was not successful",
                    this.session,
                    this.host));
            return false;
        }


    }


    protected Map getVideoStatusFromNode() {
        try {
            String response = HttpUtility.getRequestAsString(this.uri);
            if (!response.equals("")) {
                return JsonParserWrapper.toHashMap(response);
            }
        } catch (IOException e) {
            String error = String.format("Error trying to get video status from URI %s, %s\n%s",
                    this.uri,
                    e.getMessage(),
                    Throwables.getStackTraceAsString(e));

            logger.warn(error);
        } catch (URISyntaxException e) {
            String error = String.format("Error trying to get video status from URI %s, %s\n%s",
                    this.uri,
                    e.getMessage(),
                    Throwables.getStackTraceAsString(e));

            logger.warn(error);
        } catch (Exception e) { //This catch all is in case something goes wrong in parsing or something so it's not lost
            String error = String.format("Something went CATASTROPHICALLY wrong getting info form URI %s, %s\n%s",
                    this.uri,
                    e.getMessage(),
                    Throwables.getStackTraceAsString(e));

            logger.error(error);
        }

        return null;
    }

    protected URI buildVideoStatusUri() {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("http");
        builder.setHost(this.host);
        builder.setPort(3000);
        builder.setPath(TaskDescriptions.Endpoints.VIDEO);

        try {
            URI finalUri = builder.build();
            logger.info("Built URI " + finalUri);
            return finalUri;
        } catch (URISyntaxException e) {
            logger.error(
                    String.format(
                            "Error building a video status URL host: %s, port: %s, path: %s, scheme: %s",
                            builder.getHost(),
                            builder.getPort(),
                            builder.getPath(),
                            builder.getScheme()),
                    e
            );
            return null;
        }
    }
    
    private void CopyVideoFile(String session) throws IOException {
    	Gson gson = new Gson();
    	
    	try {
    		File testJSONFolder = RuntimeConfig.getConfig().getVideoRecording().getTestJSONDir();
//    		String directory = "test_JSON";
//    		File testJSONFolder = new File(directory);
    		String testJSONFile = String.format("%s.json", session);
    		logger.info(String.format("Try to copy video file for session %s", session));
    		File jsonLocation = new File(testJSONFolder, testJSONFile);
    		if (!DoesFileExist(jsonLocation)) {
    			return;
    		}
    		
    		// File found. Continue...  
    		BufferedReader br = new BufferedReader(
    			new FileReader(jsonLocation));

    		//convert the json string back to object
    		TestInfo testInfo = gson.fromJson(br, TestInfo.class);
    		
    		File newVideoFile = new File(testInfo.OutputDir, testInfo.OutputFile);
            logger.info(String.format("Copying video to hub location %s", 
            		newVideoFile.getAbsolutePath()));
    		File outputDir = RuntimeConfig.getConfig().getVideoRecording().getOutputDir();
    		String currentFile = session + ".mp4";
    		File currentVideoFile = new File(outputDir, currentFile);
    		FileUtils.copyFile(currentVideoFile, newVideoFile);

    	} catch (IOException e) {
    		e.printStackTrace();
    	}    	    	
    }
    
    private boolean DoesFileExist(File file) {
    	logger.info(String.format("Check if test's json file exists: %s",
                file.getAbsolutePath()));
    	
    	if (file.exists()) {
            logger.info(String.format("Found test's json file: %s",
                    file.getAbsolutePath()));
            return true;
        } else {
            logger.info(String.format(
                    "Test does not have a json file for this session %s. Don't copy video file.",
                    this.session));
            return false;
        }
    }
}
