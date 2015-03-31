/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class PocketSphinxActivity extends Activity implements
        RecognitionListener{

    private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "little talk";
    private static final String MENU_SEARCH = "menu";
    private static final String KEYPHRASE = "good morning";

    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;
    
    private int chosenNum = -1;
    private int recognizedNum = -1;
    private int count = 0;
    
    private File testFile;
    private String fileName;
    
    private String[] textArray;
    private String prunedText;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        captions = new HashMap<String, Integer>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        captions.put(DIGITS_SEARCH, R.string.digits_caption);
        captions.put(FORECAST_SEARCH, R.string.forecast_caption);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");
        
        
		// set the text array for file writing
		textArray = getResources().getStringArray(R.array.text_array);
		
        
        /*
         * Set the number of sentence
         */
        Spinner spinner = (Spinner) findViewById(R.id.number_spinner);
        
		// Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
		        R.array.number_array, android.R.layout.simple_spinner_item);
		
		// Specify the layout to use when the list of choices appears
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		// Apply the adapter to the spinner
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
		    @Override
		    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		        // An item was selected. You can retrieve the selected item using
		        // parent.getItemAtPosition(pos)
		    	chosenNum = Integer.valueOf((String)parent.getItemAtPosition(pos));
		    }

		    @Override
		    public void onNothingSelected(AdapterView<?> parent) {
		        // Another interface callback
		    	chosenNum = -1;
		    }
		});
		
    	
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                	//switchSearch(DIGITS_SEARCH);
                    String caption = getResources().getString(captions.get(DIGITS_SEARCH));
                    ((TextView) findViewById(R.id.caption_text)).setText(caption);
                }
            }
        }.execute();
    }
    
    
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
        	return true;
        }
        return false;
    }
    
    
    public File getAlbumStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory. 

		File file = new File(Environment.getExternalStorageDirectory(), albumName);
        Log.e("PocketSphinx", "Directory" + Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        if (!file.mkdirs()) {
            Log.e("PocketSphinx", "Directory not created");
            if (file.isDirectory()) {
            	Log.e("PocketSphinx", "Directory existed");
            }
        }

        return file;
    }
    
    
    public void openFile(View v) {
		/*
		 * init file writing
		 */
		InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(((EditText) findViewById(R.id.username)).getWindowToken(), 0);
		((EditText) findViewById(R.id.username)).clearFocus();
    	
    	
    	fileName = ((EditText) findViewById(R.id.username)).getText().toString();
    	if (TextUtils.isEmpty(fileName)) {
    		Toast.makeText(this, "Username can't be blank", Toast.LENGTH_SHORT).show();
    		return;
    	}
    	
    	testFile = new File(getAlbumStorageDir("LittleTalk"), fileName);
    	
    	if (testFile.isFile()) {
        	BufferedReader br;
    		try {
    			br = new BufferedReader(new FileReader(testFile));
    	    	count = Integer.parseInt(br.readLine().split("\\s+")[0]);
    			br.close();
    		} catch (FileNotFoundException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (NumberFormatException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	else {
    		BufferedWriter bw;
    		try {
    			bw = new BufferedWriter(new FileWriter(testFile));
    	    	count = 0;
    	    	bw.append("0     \n\n");
    			bw.close();
    		} catch (FileNotFoundException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (NumberFormatException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	
        ((TextView) findViewById(R.id.count)).setText(""+count);
    	
		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(testFile));
        sendBroadcast(intent);

    }
    
    
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
    	if (hypothesis == null)
    	    return;
    	
    	String text = hypothesis.getHypstr();
        
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(DIGITS_SEARCH))
            switchSearch(DIGITS_SEARCH);
        else if (text.equals(FORECAST_SEARCH))
            switchSearch(FORECAST_SEARCH);
        else {
            ((TextView) findViewById(R.id.result_text)).setText(text);
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        //((TextView) findViewById(R.id.result_text)).setText("");
        
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
//            ((TextView) findViewById(R.id.result_text)).setText(prunedText);

        }
    }

    @Override
    public void onBeginningOfSpeech() {
    	((Button) findViewById(R.id.startListen_button)).setEnabled(false);
    	((Button) findViewById(R.id.startListen_button)).setText("Tap to Speak");
    }

    
    @Override
    public void onEndOfSpeech() {
//      if (DIGITS_SEARCH.equals(recognizer.getSearchName()))
//  		switchSearch(DIGITS_SEARCH);
    	
    	((Button) findViewById(R.id.startListen_button)).setEnabled(true);
    	((Button) findViewById(R.id.startListen_button)).setText("Tap to Speak");
    	recognizer.stop();
    	
    	prunedText = pruneText();
    	((TextView) findViewById(R.id.result_text)).setText(prunedText);
    	
    	//save result to file
    	
    	if(testFile==null) {
    		Toast.makeText(this, "Submit your username", Toast.LENGTH_SHORT).show();
    		return;
    	}
    	
    	count++;
    	((TextView) findViewById(R.id.count)).setText(""+count);
    	
    	String outtext = "I; "+textArray[chosenNum-1] + "\nO; " + recognizedNum + "; " + prunedText + "\n\n";
    	
    	RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(testFile, "rw");
			raf.writeBytes(""+count);
	    	raf.seek(raf.length());
	    	raf.writeBytes(outtext);
	    	raf.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
    }
    
    
    public String pruneText() {
    	String[] recognizedText = ((String)((TextView) findViewById(R.id.result_text)).getText()).split("\\s+");
    	ArrayList<String> list = new ArrayList<String>(Arrays.asList(recognizedText));
    	Iterator<String> iter = list.iterator();
    	String firstWord = null;
    	
    	//deal with human language habit that we start talking from certain words
    	while(iter.hasNext()) {
        	firstWord = iter.next();
        	if(firstWord.equals("and")||firstWord.equals("or")||firstWord.equals("so")) {
        		iter.remove();
        	}
        	else break;
    	}
    	
    	if(firstWord==null||firstWord.equals("and")||firstWord.equals("or")||firstWord.equals("so")) {
    		return null;
    	}
    	
    	if(firstWord.equals("allow")) {
    		recognizedNum = 1;
    		boolean flag = false;
    		while(iter.hasNext()) {
				String word = iter.next();
				if(word.equals("choose")) {
					flag = true;
				}
				else if(word.equals("from")) {
					flag = false;
				}
				else if(flag == true) {
					iter.remove();
				}
	    	}
    	}
//    	else if(firstWord.equals("mom") || firstWord.equals("reports")) {
//    		recognizedNum = 2;
//    		boolean flag = false;
//    		while(iter.hasNext()) {
//				String word = iter.next();
//				if(word.equals("child's")) {
//					flag = true;
//				}
//				else if(word.equals("eating")) {
//					flag = false;
//				}
//				else if(flag == true) {
//					iter.remove();
//				}
//	    	}    		
//    	}
    	else if(firstWord.equals("parents")) {
    		recognizedNum = 2;
    		boolean flag = false;
    		while(iter.hasNext()) {
				String word = iter.next();
				if(word.equals("completed")) {
					flag = true;
				}
				else if(word.equals("previous")) {
					flag = false;
				}
				else if(flag == true) {
					iter.remove();
				}
	    	}    		
    	}
    	else if(firstWord.equals("begins")) {
    		recognizedNum = 3;
    		boolean flag = true;
    		while(iter.hasNext()) {
				String word = iter.next();
				if(word.equals("associative")) {
					flag = false;
				}
				else if(flag == true) {
					iter.remove();
				}
	    	}    		
    	}
    	else if(firstWord.equals("CDP")) {
    		if(iter.hasNext()) {
    			String word = iter.next();
    			if(word.equals("reviewed")||word.equals("invite")){
    				recognizedNum = 4;
    			}
    			else {
    				recognizedNum = 5;
    			}
    		}
    	}
    	else if(firstWord.equals("reviewed")||firstWord.equals("invite")) {
    		recognizedNum = 4;
    	}
    	else if(firstWord.equals("will")) {
    		recognizedNum = 5;
    	}
    	
    	else if(firstWord.equals("encourage")) {
    		if(iter.hasNext()) {
    			String word = iter.next();
    			if(word.equals("associative")) {
    				recognizedNum = 5;
    			}
    			else {
		    		recognizedNum = 6;
		    		boolean flag = false;
		    		while(iter.hasNext()) {
						String word1 = iter.next();
						if(word1.equals("exploration") || word1.equals("reading")) {
							flag = true;
						}
						else if(word1.equals("during")) {
							flag = false;
						}
						else if(flag == true) {
							iter.remove();
						}
			    	}
    			}
    		}
    	}
    	else if(firstWord.equals("start")||firstWord.equals("talk")) {
    		recognizedNum = 7;
    	}
    	else if(firstWord.equals("they")) {
    		recognizedNum = 8;
    	}
    	else if(firstWord.equals("replaces")) {
    		recognizedNum = 9;
    		boolean flag = true;
    		while(iter.hasNext()) {
				String word = iter.next();
				if(word.equals("where")) {
					flag = true;
				}
				else if(word.equals("some")||word.equals("they")) {
					flag = false;
				}
				else if(flag == true) {
					iter.remove();
				}
	    	}
    	}
    	else if(firstWord.equals("come")) {
    		recognizedNum = 10;
    		while(iter.hasNext()) {
				String word1 = iter.next();
				if(word1.equals("at")) {
					break;
				}
				if(word1.equals("with")) {
					recognizedNum = 12;
					break;
				}
	    	}
    		
    	}
    	else if(firstWord.equals("put")) {
    		recognizedNum = 11;
    	}
    	else {
    		boolean flag = true;
    		iter.remove();
    		while(iter.hasNext()) {
				String word1 = iter.next();
				if(word1.equals("put")) {
					recognizedNum = 11;
					flag = false;
				}
				else if(flag == true) {
					iter.remove();
				}
	    	}
    	}
    	
    	return joinStringArr(list.toArray(new String[list.size()]));
    }
    
    
	private String joinStringArr(String[] strArr) {
		int k = strArr.length;
		if ( k == 0 )
		{
			return null;
		}
		StringBuilder out = new StringBuilder();
		out.append(strArr[0]);
		for ( int x=1; x < k; ++x )
		{
			out.append(" ").append(strArr[x]);
		}
		return out.toString();
	}
    

    
    // start listening button onclick function
    public void startListen(View v) {
    	if (chosenNum == -1) {
    		Toast.makeText(this, "Choose an index first", Toast.LENGTH_SHORT).show();
    		return;
    	}
    	
    	((TextView) findViewById(R.id.result_text)).setText("");
    	((Button) findViewById(R.id.startListen_button)).setText("Please Speak");
    	switchSearch(DIGITS_SEARCH);
    }
    
    
    private void switchSearch(final String searchName) {
        
    	recognizer.startListening(searchName, 5);
    }

    
    private void setupRecognizer(File assetsDir) {
        File modelsDir = new File(assetsDir, "models");
        recognizer = defaultSetup()
        		.setInteger("-vad_postspeech", 100)
        		.setFloat("-vad_threshold", 3.2)
                .setAcousticModel(new File(modelsDir, "hmm/en-us-semi"))
                .setDictionary(new File(modelsDir, "dict/cmu07a.dic"))
                .setRawLogDir(assetsDir)
                .setKeywordThreshold(1e-20f)
                .getRecognizer();
        recognizer.addListener(this);

        File digitsGrammar = new File(modelsDir, "grammar/little_talk.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
//        File digitsGrammar = new File(modelsDir, "lm/8307.lm");
//        recognizer.addNgramSearch(DIGITS_SEARCH, digitsGrammar);
    }
}
