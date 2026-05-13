package com.place1.aitmtrainer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.widget.*;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 100;
    // v2.2: 관리자 서버에 실제 대화 API가 붙기 전까지는 앱 내부 즉시 응답으로 테스트합니다.
    // 시작/응답 시 서버 API를 기다리지 않아 로딩이 빨라집니다.
    private static final boolean USE_SERVER_CHAT_API = false;
    private String lastPartialText = "";
    private LinearLayout setupPanel, callPanel;
    private EditText serverEdit, employeeEdit;
    private Spinner positionSpinner, customerTypeSpinner, difficultySpinner;
    private Button loadButton, startButton, pauseButton, finishButton;
    private TextView statusView, callStateView, callMetaView, goalView, timerView, micHintView;
    private JSONArray positions = new JSONArray();
    private JSONArray customerTypes = new JSONArray();
    private String currentPositionCode = "newbie";
    private Integer sessionId = null;
    private boolean paused = false, aiSpeaking = false, finishing = false;
    private Handler handler = new Handler();
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences prefs;
    private long callStartMillis = 0L;
    private Runnable timerRunnable;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ai_tm_trainer", MODE_PRIVATE);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }
        tts = new TextToSpeech(this, this);
        setupSpeechRecognizer();
        buildUi();
        loadInitialData();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.KOREAN);
            tts.setSpeechRate(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { aiSpeaking = true; }
                @Override public void onDone(String id) { aiSpeaking = false; if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 1500); }
                @Override public void onError(String id) { aiSpeaking = false; if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 1200); }
            });
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { lastPartialText = ""; setCallState("듣는 중"); setStatus("말씀하세요"); setMicHint("충분히 말씀하세요. 잠깐의 침묵은 기다립니다"); }
            @Override public void onBeginningOfSpeech() { setMicHint("말씀을 듣고 있습니다. 끝까지 말씀하세요"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { setCallState("AI 생각 중"); setStatus("음성 분석 중"); setMicHint("말씀을 정리하고 있습니다"); }
            @Override public void onError(int error) {
                if (paused || finishing || aiSpeaking) return;

                // v2.2: 짧게 끊겼거나 최종 인식 실패가 나와도 팝업/토스트 없이 조용히 재청취합니다.
                // 단, 부분 인식 문장이 있으면 그 문장을 상담원 발화로 사용합니다.
                if (lastPartialText != null && lastPartialText.trim().length() >= 3) {
                    String captured = lastPartialText.trim();
                    lastPartialText = "";
                    sendEmployeeMessage(captured);
                    return;
                }

                setCallState("듣는 중");
                setStatus("계속 듣는 중");
                setMicHint("편하게 이어서 말씀하세요");
                handler.postDelayed(() -> startListeningSilently(), 900);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0).trim() : "";
                if (text.length() == 0) { if (lastPartialText != null && lastPartialText.trim().length() >= 3) { text = lastPartialText.trim(); } else { if (!paused && !finishing) handler.postDelayed(() -> startListeningSilently(), 900); return; } }
                sendEmployeeMessage(text);
            }
            @Override public void onPartialResults(Bundle partialResults) { ArrayList<String> p = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (p != null && !p.isEmpty()) lastPartialText = p.get(0); }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(18)); root.setBackgroundColor(Color.rgb(246,248,252));
        TextView title = new TextView(this); title.setText("AI TM 콜드콜 트레이닝"); title.setTextSize(24); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(Color.rgb(15,23,42)); root.addView(title);
        TextView sub = new TextView(this); sub.setText("실제 통화처럼 자동으로 듣고 답변합니다"); sub.setTextSize(14); sub.setTextColor(Color.rgb(100,116,139)); sub.setPadding(0,dp(4),0,dp(14)); root.addView(sub);
        ScrollView scroll = new ScrollView(this); setupPanel = new LinearLayout(this); setupPanel.setOrientation(LinearLayout.VERTICAL); setupPanel.setPadding(dp(18),dp(18),dp(18),dp(18)); setupPanel.setBackground(bg(Color.WHITE, dp(18), Color.rgb(226,232,240))); scroll.addView(setupPanel); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setupPanel.addView(label("서버 주소")); serverEdit = input(); serverEdit.setText(prefs.getString("server_url", "http://172.30.0.53:8031")); setupPanel.addView(serverEdit);
        loadButton = button("서버 연결 / 포지션 / 유형 불러오기", true); loadButton.setOnClickListener(v->{ saveServerUrl(); loadInitialData(); }); setupPanel.addView(loadButton);
        setupPanel.addView(section("훈련 설정"));
        setupPanel.addView(label("이름")); employeeEdit = input(); employeeEdit.setHint("예: 김민수"); employeeEdit.setText(prefs.getString("employee_name", "")); setupPanel.addView(employeeEdit);
        setupPanel.addView(label("포지션")); positionSpinner = spinner(); setupPanel.addView(positionSpinner);
        setupPanel.addView(label("고객 유형")); customerTypeSpinner = spinner(); setupPanel.addView(customerTypeSpinner);
        setupPanel.addView(label("난이도")); difficultySpinner = spinner(); difficultySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"초급","중급","고급"})); setupPanel.addView(difficultySpinner);
        goalView = info("목표: 자료 전송 동의 · 팀장 토스 · 재통화 일정 확정"); setupPanel.addView(goalView);
        startButton = button("시작하기", true); startButton.setTextSize(18); startButton.setOnClickListener(v -> startSession()); setupPanel.addView(startButton);

        callPanel = new LinearLayout(this); callPanel.setOrientation(LinearLayout.VERTICAL); callPanel.setGravity(Gravity.CENTER_HORIZONTAL); callPanel.setPadding(dp(18),dp(22),dp(18),dp(18)); callPanel.setVisibility(View.GONE); root.addView(callPanel,new LinearLayout.LayoutParams(-1,0,1));
        callMetaView = new TextView(this); callMetaView.setGravity(Gravity.CENTER); callMetaView.setTextSize(15); callMetaView.setTextColor(Color.rgb(71,85,105)); callPanel.addView(callMetaView);
        timerView = new TextView(this); timerView.setText("00:00"); timerView.setTextSize(42); timerView.setTypeface(Typeface.DEFAULT_BOLD); timerView.setTextColor(Color.rgb(37,99,235)); timerView.setGravity(Gravity.CENTER); timerView.setPadding(0,dp(34),0,dp(8)); callPanel.addView(timerView);
        callStateView = new TextView(this); callStateView.setText("대기 중"); callStateView.setTextSize(28); callStateView.setTypeface(Typeface.DEFAULT_BOLD); callStateView.setTextColor(Color.rgb(15,23,42)); callStateView.setGravity(Gravity.CENTER); callPanel.addView(callStateView,new LinearLayout.LayoutParams(-1,-2));
        micHintView = new TextView(this); micHintView.setText("통화 준비 중"); micHintView.setTextSize(15); micHintView.setTextColor(Color.rgb(100,116,139)); micHintView.setGravity(Gravity.CENTER); micHintView.setPadding(0,dp(12),0,dp(28)); callPanel.addView(micHintView);
        TextView guide = info("텍스트는 표시하지 않습니다.\nAI가 말한 뒤 자동으로 상담원 음성을 듣습니다.\n말씀이 없으면 조용히 다시 듣습니다."); guide.setGravity(Gravity.CENTER); callPanel.addView(guide);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,dp(24),0,0);
        pauseButton = button("일시정지", false); pauseButton.setOnClickListener(v -> togglePause()); row.addView(pauseButton,new LinearLayout.LayoutParams(0,dp(52),1)); Space sp = new Space(this); row.addView(sp,new LinearLayout.LayoutParams(dp(10),1));
        finishButton = button("종료/평가", true); finishButton.setOnClickListener(v -> finishSession()); row.addView(finishButton,new LinearLayout.LayoutParams(0,dp(52),1)); callPanel.addView(row,new LinearLayout.LayoutParams(-1,-2));
        statusView = new TextView(this); statusView.setText("상태: 준비 중"); statusView.setTextColor(Color.rgb(71,85,105)); statusView.setPadding(0,dp(10),0,0); root.addView(statusView);
        setContentView(root);
    }

    private TextView section(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(18);v.setTypeface(Typeface.DEFAULT_BOLD);v.setTextColor(Color.rgb(15,23,42));v.setPadding(0,dp(22),0,dp(8));return v;}
    private TextView label(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(13);v.setTypeface(Typeface.DEFAULT_BOLD);v.setTextColor(Color.rgb(52,64,84));v.setPadding(0,dp(12),0,dp(4));return v;}
    private EditText input(){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setPadding(dp(12),0,dp(12),0);e.setBackground(bg(Color.WHITE,dp(12),Color.rgb(203,213,225)));e.setMinHeight(dp(48));return e;}
    private Spinner spinner(){Spinner s=new Spinner(this);s.setMinimumHeight(dp(48));s.setBackground(bg(Color.WHITE,dp(12),Color.rgb(203,213,225)));return s;}
    private TextView info(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(14);v.setTextColor(Color.rgb(71,85,105));v.setPadding(dp(14),dp(12),dp(14),dp(12));v.setBackground(bg(Color.rgb(248,250,252),dp(14),Color.rgb(226,232,240)));return v;}
    private Button button(String text, boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(primary?Color.WHITE:Color.rgb(37,99,235));b.setBackground(primary?grad():bg(Color.rgb(239,246,255),dp(14),Color.rgb(147,197,253)));b.setMinHeight(dp(50));return b;}
    private GradientDrawable bg(int color,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);g.setStroke(1,stroke);return g;}
    private GradientDrawable grad(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(37,99,235),Color.rgb(124,58,237)});g.setCornerRadius(dp(14));return g;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}    
    private void setStatus(String t){runOnUiThread(()->statusView.setText("상태: "+t));}
    private void setCallState(String t){runOnUiThread(()->callStateView.setText(t));}
    private void setMicHint(String t){runOnUiThread(()->micHintView.setText(t));}
    private void saveServerUrl(){prefs.edit().putString("server_url",server()).apply();}
    private String server(){return serverEdit.getText().toString().trim().replaceAll("/+$","");}

    private JSONObject requestJson(String path,String method,JSONObject body)throws Exception{URL url=new URL(server()+path);HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod(method);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setConnectTimeout(15000);c.setReadTimeout(90000);if(body!=null){c.setDoOutput(true);OutputStream o=c.getOutputStream();o.write(body.toString().getBytes("UTF-8"));o.flush();o.close();}InputStream is=(c.getResponseCode()>=200&&c.getResponseCode()<300)?c.getInputStream():c.getErrorStream();String tx=readAll(is);if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new RuntimeException(tx);return new JSONObject(tx);}
    private JSONArray requestJsonArray(String path)throws Exception{URL url=new URL(server()+path);HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("GET");c.setConnectTimeout(15000);c.setReadTimeout(60000);return new JSONArray(readAll(c.getInputStream()));}
    private String readAll(InputStream is)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"));StringBuilder sb=new StringBuilder();String l;while((l=br.readLine())!=null)sb.append(l);br.close();return sb.toString();}

    private void loadInitialData(){new Thread(()->{try{saveServerUrl();requestJson("/api/health","GET",null);positions=requestJsonArray("/api/positions");ArrayList<String> names=new ArrayList<>();for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);if(p.optInt("is_active",1)==1)names.add(p.getString("name"));}runOnUiThread(()->{positionSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));positionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){@Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){try{currentPositionCode=findPositionCodeByName(String.valueOf(positionSpinner.getSelectedItem()));loadCustomerTypes();updateGoalText();}catch(Exception ignored){}}@Override public void onNothingSelected(android.widget.AdapterView<?> parent){}});});setStatus("서버 연결 완료");loadCustomerTypes();}catch(Exception e){setStatus("서버 연결 실패: "+e.getMessage());}}).start();}
    private String findPositionCodeByName(String name)throws Exception{for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);if(name.equals(p.getString("name")))return p.getString("code");}return "newbie";}
    private void updateGoalText(){try{JSONArray goals=null;for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);if(currentPositionCode.equals(p.getString("code")))goals=p.optJSONArray("goals");}ArrayList<String> gnames=new ArrayList<>();if(goals!=null){for(int i=0;i<goals.length();i++){JSONObject g=goals.getJSONObject(i);if(g.optInt("is_active",1)==1&&g.optInt("is_core",1)==1)gnames.add(g.optString("title",""));}}runOnUiThread(()->goalView.setText("목표: "+join(gnames," · ")));}catch(Exception ignored){}}
    private String join(ArrayList<String> a,String sep){StringBuilder sb=new StringBuilder();for(int i=0;i<a.size();i++){if(i>0)sb.append(sep);sb.append(a.get(i));}return sb.toString();}
    private void loadCustomerTypes(){new Thread(()->{try{customerTypes=requestJsonArray("/api/customer-types?position="+currentPositionCode);ArrayList<String> names=new ArrayList<>();for(int i=0;i<customerTypes.length();i++){JSONObject item=customerTypes.getJSONObject(i);if(item.optInt("is_active",1)==1)names.add(item.getString("name"));}runOnUiThread(()->{customerTypeSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));setStatus("포지션/유형 불러오기 완료");});}catch(Exception e){setStatus("유형 불러오기 실패: "+e.getMessage());}}).start();}
    private int selectedCustomerTypeId()throws Exception{String n=String.valueOf(customerTypeSpinner.getSelectedItem());for(int i=0;i<customerTypes.length();i++){JSONObject item=customerTypes.getJSONObject(i);if(n.equals(item.getString("name")))return item.getInt("id");}return 1;}
    private String selectedCustomerTypeName(){Object o=customerTypeSpinner.getSelectedItem();return o==null?"-":String.valueOf(o);}private String selectedPositionName(){Object o=positionSpinner.getSelectedItem();return o==null?"-":String.valueOf(o);}

    private void startSession(){
        String emp=employeeEdit.getText().toString().trim();
        if(emp.isEmpty()){
            Toast.makeText(this,"이름을 입력하세요.",Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("employee_name",emp).putString("server_url",server()).apply();
        paused=false;
        finishing=false;
        sessionId=-1;
        callStartMillis=System.currentTimeMillis();

        // v2.2 핵심 수정: 시작하기를 누르면 서버 대화 API를 기다리지 않고 즉시 통화 화면으로 진입합니다.
        setupPanel.setVisibility(View.GONE);
        callPanel.setVisibility(View.VISIBLE);
        callMetaView.setText(selectedPositionName()+" / "+selectedCustomerTypeName()+" / "+difficultySpinner.getSelectedItem());
        pauseButton.setText("일시정지");
        startTimer();
        setStatus("훈련 시작");
        speakAi(fallbackFirstMessage());
    }
    private String fallbackFirstMessage(){String n=selectedCustomerTypeName();if(n.contains("바쁜"))return "네, 지금 바쁜데요.";if(n.contains("의심"))return "네, 말씀하세요.";if(n.contains("가격"))return "여보세요, 네.";return "여보세요?";}
    private void startTimer(){if(timerRunnable!=null)handler.removeCallbacks(timerRunnable);timerRunnable=new Runnable(){@Override public void run(){long sec=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);timerView.setText(String.format(Locale.KOREA,"%02d:%02d",sec/60,sec%60));if(!finishing)handler.postDelayed(this,1000);}};handler.post(timerRunnable);}    
    private void togglePause(){paused=!paused;if(paused){stopListening();setCallState("일시정지");setStatus("일시정지됨");setMicHint("다시 시작을 누르면 이어집니다");pauseButton.setText("다시 시작");}else{pauseButton.setText("일시정지");setStatus("다시 듣기 시작");startListeningSilently();}}
    private void startListeningSilently(){if(paused||finishing||aiSpeaking||speechRecognizer==null)return;try{stopListening();Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,3500);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,2500);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,3000);speechRecognizer.startListening(i);}catch(Exception e){setStatus("음성 인식 재시도 중");handler.postDelayed(()->startListeningSilently(),1200);}}
    private void stopListening(){try{if(speechRecognizer!=null)speechRecognizer.cancel();}catch(Exception ignored){}}
    private void sendEmployeeMessage(String text){
        stopListening();
        lastPartialText = "";
        setCallState("AI 생각 중");
        setStatus("AI 고객 답변 생성 중");
        setMicHint("AI가 답변을 준비하고 있습니다");

        if(!USE_SERVER_CHAT_API){
            handler.postDelayed(() -> speakAi(fallbackReply(text)), 350);
            return;
        }

        new Thread(()->{
            try{
                JSONObject body=new JSONObject();
                body.put("session_id",sessionId);
                body.put("message",text);
                JSONObject res=requestJson("/api/chat","POST",body);
                speakAi(res.getString("reply"));
            }catch(Exception e){
                speakAi(fallbackReply(text));
            }
        }).start();
    }
    private String fallbackReply(String text){String type=selectedCustomerTypeName();if(type.contains("가격")){if(text.contains("자료")||text.contains("확인"))return "그러면 자료로 먼저 볼 수 있다는 말씀이세요?";if(text.contains("팀장"))return "팀장님이 짧게 설명해주실 수 있으면 들어볼게요.";if(text.contains("시간")||text.contains("내일")||text.contains("오후"))return "그럼 그 시간에 다시 통화하면 되는 건가요?";return "근데 이거 비용이 드는 거죠? 대략 어느 정도인지 알아야 판단할 수 있어요.";}if(type.contains("의심")){if(text.contains("자료")||text.contains("사례"))return "그럼 자료에 실제 확인할 수 있는 내용이 있나요?";if(text.contains("팀장"))return "팀장님이 직접 설명해주시면 짧게는 들어볼게요.";return "혹시 광고대행 쪽인가요? 예전에 맡겼다가 효과를 못 봐서요.";}if(type.contains("바쁜")){if(text.contains("20초")||text.contains("짧게"))return "네, 그럼 짧게만 말씀해보세요.";if(text.contains("자료"))return "그럼 자료로 보내주세요. 보고 필요하면 연락드릴게요.";return "지금 바빠서요. 핵심만 짧게 말씀해주세요.";}return "네, 어떤 내용인지 조금 더 말씀해보세요.";}
    private void speakAi(String text){runOnUiThread(()->{aiSpeaking=true;stopListening();setCallState("AI 고객 말하는 중");setStatus("AI 고객 응답 중");setMicHint("AI 고객이 말하고 있습니다");tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"ai_customer_"+System.currentTimeMillis());});}
    private void finishSession(){finishing=true;paused=true;stopListening();stopAudio();long duration=Math.max(0,(System.currentTimeMillis()-callStartMillis)/1000);setCallState("평가 중");setStatus("평가 리포트 생성 중");setMicHint("훈련을 종료합니다");new Thread(()->{try{if(sessionId!=null&&sessionId>0)requestJson("/api/sessions/"+sessionId+"/finish","POST",new JSONObject());}catch(Exception ignored){}runOnUiThread(()->{Toast.makeText(this,"훈련 종료 / 통화시간 "+duration+"초",Toast.LENGTH_LONG).show();setupPanel.setVisibility(View.VISIBLE);callPanel.setVisibility(View.GONE);});setStatus(duration>=180?"유효 훈련 완료":"3분 미만 종료: 관리자 확인 대상");}).start();}
    private void stopAudio(){try{if(tts!=null)tts.stop();}catch(Exception ignored){}}
    @Override protected void onDestroy(){super.onDestroy();finishing=true;stopListening();if(speechRecognizer!=null){speechRecognizer.destroy();speechRecognizer=null;}stopAudio();if(tts!=null)tts.shutdown();}
}
