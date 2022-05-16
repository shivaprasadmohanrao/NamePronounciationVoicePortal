package com.wf.hackathon2022.controller;

import static spark.Spark.post;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Hangup;
import com.twilio.twiml.voice.Pause;
import com.twilio.twiml.voice.Record;
import com.twilio.twiml.voice.Say;


public class NameRecorderTool {
	
	static VoiceResponse twiml = null;//for generating twiml(twilio xml) for voice dialog control
	static RestTemplate restTemplate = new RestTemplate();
	static String languageSelected = "";//user selected Language
	public static String userLastIntent = ""; 
	public static String mediaFile = "";
	public static String userPhoneNumber = "";
	public static String empId = "";
	static RestTemplateBuilder restTbc = new RestTemplateBuilder(new RestTemplateCustomizer() {
		@Override
		public void customize(RestTemplate restTemplate) {
			restTemplate = NameRecorderTool.restTemplate;
		}
	});
	
	//SparkJava Main method - Start of the program
	public static void main(String[] args) {
		//serve static files like pdf, jpeg, media files from this project over the http (internet)
		//Spark.staticFiles.location("/static");

		// 1. Get the employee id
		post("record-call", (req, res) -> {
			System.out.println("1. Name Recording Interaction Started.");
			//prepare first Dialog prompt, expected to collect User preferred language
			Say welcome = new Say.Builder("Welcome to name recorder voice portal.").
					voice(Say.Voice.POLLY_ADITI).build();
			Say enterempId = new Say.Builder("Please enter your 7 digit employee number or I Dee").voice(Say.Voice.POLLY_ADITI).build();
			Pause pause = new Pause.Builder().length(2).build();
			Gather gatherLanguage = new Gather.Builder().timeout(10).numDigits(7).action("get-empid").inputs(Gather.Input.DTMF).say(welcome).pause(pause).say(enterempId).build();
			twiml= new VoiceResponse.Builder().gather(gatherLanguage).build();

			return twiml.toXml();

		});

		// 2. Record user input  
		post("get-empid", (req, res) -> {
			String welcomeMessage = "";
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("2. User entered employee id ======> " + map.get("Digits"));
			empId = map.get("Digits");
					welcomeMessage = "Thank you. Please say your name after the beep. ";
					languageSelected = "en-IN";
			Say welcome = new Say.Builder(welcomeMessage).voice(Say.Voice.POLLY_ADITI).build();
			Record record = new Record.Builder().maxLength(10).playBeep(true).action("/post-recording-action").build();
			twiml = new VoiceResponse.Builder().say(welcome).record(record).build();
			return twiml.toXml();

		});

		// 3. Convert recorded file and save recording 
		post("post-recording-action", (req, res) -> {
			
			System.out.println("3. Audio Recording for caller Name.");
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("4. Recording URL =======> " + map.get("RecordingUrl"));
			// Get recording URL here
			saveAudioFile(map.get("RecordingUrl"));
			saveAudio("1747154",System.getProperty("user.dir") + "/target/classes/static/empid_" + empId+".wav");
			//String audioPath = convertFile();
			Say sayPhoneNumber = new Say.Builder("Your name is saved with us. Thank you for calling name recorder voice portal. Good bye !").voice(Say.Voice.POLLY_ADITI).build();
			//Play play = new Play.Builder("http://db57-49-207-224-149.ngrok.io/Cert.wav").build();
			twiml = new VoiceResponse.Builder().say(sayPhoneNumber).hangup(new Hangup.Builder().build()).build();
			return twiml.toXml();
		});
		post("thank-you", (req, res) -> {
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("Inside Thank You ======= " + map.get("Digits"));
			userPhoneNumber = map.get("Digits");
					
			Say say1 = new Say.Builder("Your name is saved with us. Thank you for calling voice portal. Good bye!").voice(Say.Voice.POLLY_ADITI).build();

			twiml = new VoiceResponse.Builder().say(say1).build();
			return twiml.toXml();

			});
		//end

	}

	
	// - change the audio file path save location 
	//The audio content recorded from user are saved using this utility method in wav format
	public static void saveAudioFile(String recordingUrl) {
		System.out.println("5. SaveAudioFile Service Started..");
		URLConnection conn;
		try {
			conn = new URL(recordingUrl).openConnection();
			InputStream is = conn.getInputStream();
			//Change this to local path to save audio recordings before conversion
			File src = new File(System.getProperty("user.dir") + "/target/classes/static/empid_" + empId+".wav");
			System.out.println("6. Name Recording is stored at: " + src.getAbsolutePath());
			OutputStream outstream = new FileOutputStream(src);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = is.read(buffer)) > 0) {
				outstream.write(buffer, 0, len);
//        		File dest = new File(System.getProperty("user.dir") + "/target/classes/static/FinalRec.wav");
//        		FileUtils.copyFile(src,dest);
			}
			outstream.close();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("7. SaveAudioFile Service Completed.");
	}
	//this needs to move to service package
	public static void saveAudio(String empId,String audioUrl) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("multipartFile", getTestFile(audioUrl));
		body.add("empId", "17471565");
		body.add("channel", "ivr");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

		String serverUrl = "https://92d2-124-123-173-69.in.ngrok.io/api/v1/npsrecords/updateEmpAudioRecord";

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.postForEntity(serverUrl, requestEntity, String.class);
		System.out.println("Audio Save Status " + response.getStatusCodeValue());

	}
	
	public static Resource getTestFile(String audioUrl) throws IOException {
        File f = new File(audioUrl);
        System.out.println("Creating and Uploading Audio File: ");
        return new FileSystemResource(f);
    }
	
	//utility method to parse api response 4315 8123 4337 1001 09/28 
	public static Map<String, String> asMap(String urlencoded, String encoding) throws UnsupportedEncodingException {

		Map<String, String> map = new LinkedHashMap<>();
		for (String keyValue : urlencoded.trim().split("&")) {

			String[] tokens = keyValue.trim().split("=");
			String key = tokens[0];
			String value = tokens.length == 1 ? null : URLDecoder.decode(tokens[1], encoding);
			map.put(key, value);
		}
		return map;
	}

}
//End
