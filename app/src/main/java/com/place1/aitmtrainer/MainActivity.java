package com.place1.aitmtrainer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 100;
    private static final int REQ_SPEECH = 101;

    private LinearLayout setupPanel;
    private LinearLayout callPanel;

    private EditText serverEdit;
    private EditText employeeEdit;
    private Spinner positionSpinner;
    private Spinner customerTypeSpinner;
    private Spinner difficultySpinner;
    private Button loadButton;
    private Button startButton;
    private Button pauseButton;
    private Button finishButton;
    private TextView statusView;
    private TextView callStateView;
    private TextView callMetaView;
    private TextView goalView;

    private JSONArray positions = new JSONArray();
    private JSONArray customerTypes = new JSONArray();

    private String currentPositionCode = "newbie";
    private Integer sessionId = null;
    private String currentVoiceStyle = "";
    private boolean useOpenAiTts = false;
    private boolean paused = false;
    private boolean aiSpeaking = false;

    private Handler handler = new Handler();
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;
    private SharedPreferences prefs;

    private long callStartMillis = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ai_tm_trainer", MODE_PRIVATE);
        tts = new TextToSpeech(this, this);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        }

        buildUi();
        loadInitialData();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.KOREAN);
            tts.setSpeechRate(1.0f);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(26, 26, 26, 26);
        root.setBackgroundColor(Color.rgb(246, 248, 252));

        TextView title = new TextView(this);
        title.setText("AI TM 콜드콜 트레이닝");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(16, 24, 40));
        title.setPadding(0, 0, 0, 18);
        root.addView(title);

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(20, 20, 20, 20);
        setupPanel.setBackgroundColor(Color.WHITE);
        root.addView(setupPanel, new LinearLayout.LayoutParams(-1, -2));

        setupPanel.addView(label("서버 주소"));
        serverEdit = new EditText(this);
        serverEdit.setSingleLine(true);
        serverEdit.setText(prefs.getString("server_url", "http://172.30.0.53:8031"));
        setupPanel.addView(serverEdit);

        loadButton = new Button(this);
        loadButton.setText("서버 연결 / 포지션 / 유형 불러오기");
        loadButton.setOnClickListener(v -> {
            saveServerUrl();
            loadInitialData();
        });
        setupPanel.addView(loadButton);

        setupPanel.addView(label("이름"));
        employeeEdit = new EditText(this);
        employeeEdit.setSingleLine(true);
        employeeEdit.setHint("예: 김민수");
        employeeEdit.setText(prefs.getString("employee_name", ""));
        setupPanel.addView(employeeEdit);

        setupPanel.addView(label("포지션"));
        positionSpinner = new Spinner(this);
        setupPanel.addView(positionSpinner);

        setupPanel.addView(label("고객 유형"));
        customerTypeSpinner = new Spinner(this);
        setupPanel.addView(customerTypeSpinner);

        setupPanel.addView(label("난이도"));
        difficultySpinner = new Spinner(this);
        ArrayAdapter<String> diffAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"초급", "중급", "고급"});
        difficultySpinner.setAdapter(diffAdapter);
        setupPanel.addView(difficultySpinner);

        goalView = new TextView(this);
        goalView.setText("목표: 자료 전송 동의 · 팀장 토스 · 재통화 일정 확정");
        goalView.setTextSize(14);
        goalView.setTextColor(Color.rgb(71, 85, 105));
        goalView.setPadding(4, 14, 4, 14);
        setupPanel.addView(goalView);

        startButton = new Button(this);
        startButton.setText("시작하기");
        startButton.setTextSize(18);
        startButton.setOnClickListener(v -> startSession());
        setupPanel.addView(startButton);

        callPanel = new LinearLayout(this);
        callPanel.setOrientation(LinearLayout.VERTICAL);
        callPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        callPanel.setPadding(20, 42, 20, 20);
        callPanel.setVisibility(View.GONE);
        root.addView(callPanel, new LinearLayout.LayoutParams(-1, 0, 1));

        callMetaView = new TextView(this);
        callMetaView.setGravity(Gravity.CENTER);
        callMetaView.setTextSize(14);
        callMetaView.setTextColor(Color.rgb(71, 85, 105));
        callPanel.addView(callMetaView);

        callStateView = new TextView(this);
        callStateView.setText("대기 중");
        callStateView.setTextSize(28);
        callStateView.setTextColor(Color.rgb(15, 23, 42));
        callStateView.setGravity(Gravity.CENTER);
        callStateView.setPadding(0, 80, 0, 60);
        callPanel.addView(callStateView, new LinearLayout.LayoutParams(-1, -2));

        TextView helper = new TextView(this);
        helper.setText("텍스트 없이 실제 통화처럼 진행됩니다.\nAI가 말한 뒤 자동으로 듣기 상태가 됩니다.");
        helper.setGravity(Gravity.CENTER);
        helper.setTextSize(15);
        helper.setTextColor(Color.rgb(71, 85, 105));
        helper.setPadding(0, 0, 0, 30);
        callPanel.addView(helper);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        pauseButton = new Button(this);
        pauseButton.setText("일시정지");
        pauseButton.setOnClickListener(v -> togglePause());
        btnRow.addView(pauseButton, new LinearLayout.LayoutParams(0, -2, 1));

        finishButton = new Button(this);
        finishButton.setText("종료/평가");
        finishButton.setOnClickListener(v -> finishSession());
        btnRow.addView(finishButton, new LinearLayout.LayoutParams(0, -2, 1));

        callPanel.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("상태: 준비 중");
        statusView.setTextColor(Color.rgb(71, 85, 105));
        statusView.setPadding(0, 18, 0, 0);
        root.addView(statusView);

        setContentView(root);
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(13);
        v.setTextColor(Color.rgb(52, 64, 84));
        v.setPadding(0, 14, 0, 4);
        return v;
    }

    private void saveServerUrl() {
        prefs.edit().putString("server_url", server()).apply();
    }

    private String server() {
        return serverEdit.getText().toString().trim().replaceAll("/+$", "");
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusView.setText("상태: " + text));
    }

    private void setCallState(String text) {
        runOnUiThread(() -> callStateView.setText(text));
    }

    private JSONObject requestJson(String path, String method, JSONObject body) throws Exception {
        URL url = new URL(server() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(90000);

        if (body != null) {
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
        }

        InputStream is = (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(is);
        if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) throw new RuntimeException(text);
        return new JSONObject(text);
    }

    private JSONArray requestJsonArray(String path) throws Exception {
        URL url = new URL(server() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        return new JSONArray(readAll(conn.getInputStream()));
    }

    private String readAll(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void loadInitialData() {
        new Thread(() -> {
            try {
                saveServerUrl();
                JSONObject health = requestJson("/api/health", "GET", null);
                useOpenAiTts = health.optBoolean("use_openai_tts", false);

                JSONObject posResult = requestJson("/api/positions", "GET", null);
            } catch (Exception e) {
                // /api/positions returns array, so use fallback below.
            }

            try {
                positions = requestJsonArray("/api/positions");
                ArrayList<String> positionNames = new ArrayList<>();
                for (int i = 0; i < positions.length(); i++) {
                    JSONObject p = positions.getJSONObject(i);
                    if (p.optInt("is_active", 1) == 1) positionNames.add(p.getString("name"));
                }
                runOnUiThread(() -> {
                    positionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, positionNames));
                    positionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            try {
                                currentPositionCode = findPositionCodeByName(String.valueOf(positionSpinner.getSelectedItem()));
                                loadCustomerTypes();
                                updateGoalText();
                            } catch (Exception ignored) {}
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });
                });
                setStatus("포지션 불러오기 완료");
                loadCustomerTypes();
            } catch (Exception e) {
                setStatus("서버 연결 실패: " + e.getMessage());
            }
        }).start();
    }

    private String findPositionCodeByName(String name) throws Exception {
        for (int i = 0; i < positions.length(); i++) {
            JSONObject p = positions.getJSONObject(i);
            if (name.equals(p.getString("name"))) return p.getString("code");
        }
        return "newbie";
    }

    private JSONObject currentPositionObject() {
        try {
            for (int i = 0; i < positions.length(); i++) {
                JSONObject p = positions.getJSONObject(i);
                if (currentPositionCode.equals(p.getString("code"))) return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateGoalText() {
        try {
            JSONObject p = currentPositionObject();
            if (p == null) return;
            JSONArray goals = p.optJSONArray("goals");
            ArrayList<String> goalNames = new ArrayList<>();
            if (goals != null) {
                for (int i = 0; i < goals.length(); i++) {
                    JSONObject g = goals.getJSONObject(i);
                    if (g.optInt("is_active", 1) == 1 && g.optInt("is_core", 1) == 1) {
                        goalNames.add(g.optString("title", ""));
                    }
                }
            }
            runOnUiThread(() -> goalView.setText("목표: " + join(goalNames, " · ")));
        } catch (Exception ignored) {}
    }

    private String join(ArrayList<String> arr, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(arr.get(i));
        }
        return sb.toString();
    }

    private void loadCustomerTypes() {
        new Thread(() -> {
            try {
                customerTypes = requestJsonArray("/api/customer-types?position=" + currentPositionCode);
                ArrayList<String> names = new ArrayList<>();
                for (int i = 0; i < customerTypes.length(); i++) {
                    JSONObject item = customerTypes.getJSONObject(i);
                    if (item.optInt("is_active", 1) == 1) names.add(item.getString("name"));
                }
                runOnUiThread(() -> {
                    customerTypeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                    setStatus("유형 불러오기 완료");
                });
            } catch (Exception e) {
                setStatus("유형 불러오기 실패: " + e.getMessage());
            }
        }).start();
    }

    private int selectedCustomerTypeId() throws Exception {
        String selectedName = String.valueOf(customerTypeSpinner.getSelectedItem());
        for (int i = 0; i < customerTypes.length(); i++) {
            JSONObject item = customerTypes.getJSONObject(i);
            if (selectedName.equals(item.getString("name"))) return item.getInt("id");
        }
        return 1;
    }

    private String selectedCustomerTypeName() {
        Object o = customerTypeSpinner.getSelectedItem();
        return o == null ? "-" : String.valueOf(o);
    }

    private String selectedPositionName() {
        Object o = positionSpinner.getSelectedItem();
        return o == null ? "-" : String.valueOf(o);
    }

    private void startSession() {
        String employee = employeeEdit.getText().toString().trim();
        if (employee.isEmpty()) {
            Toast.makeText(this, "이름을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("employee_name", employee).putString("server_url", server()).apply();

        paused = false;
        sessionId = null;
        callStartMillis = System.currentTimeMillis();

        setStatus("훈련 시작 중");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("employee_name", employee);
                body.put("position_code", currentPositionCode);
                body.put("customer_type_id", selectedCustomerTypeId());
                body.put("difficulty", String.valueOf(difficultySpinner.getSelectedItem()));

                JSONObject res;
                try {
                    // 다음 단계의 통합 서버에서 구현할 정식 엔드포인트
                    res = requestJson("/api/sessions/start", "POST", body);
                } catch (Exception e) {
                    // 관리자 서버만 연결된 상태에서도 앱 동작 확인이 가능하도록 임시 시작
                    res = new JSONObject();
                    res.put("session_id", -1);
                    res.put("first_message", fallbackFirstMessage());
                    res.put("voice_style", "");
                    res.put("use_openai_tts", false);
                }

                sessionId = res.getInt("session_id");
                currentVoiceStyle = res.optString("voice_style", "");
                useOpenAiTts = res.optBoolean("use_openai_tts", false);
                String first = res.getString("first_message");

                runOnUiThread(() -> {
                    setupPanel.setVisibility(View.GONE);
                    callPanel.setVisibility(View.VISIBLE);
                    callMetaView.setText(selectedPositionName() + " / " + selectedCustomerTypeName() + " / " + difficultySpinner.getSelectedItem());
                    pauseButton.setText("일시정지");
                });

                speakAi(first, true);
            } catch (Exception e) {
                setStatus("훈련 시작 실패: " + e.getMessage());
            }
        }).start();
    }

    private String fallbackFirstMessage() {
        String name = selectedCustomerTypeName();
        if (name.contains("바쁜")) return "네, 지금 바쁜데요.";
        if (name.contains("의심")) return "네, 말씀하세요.";
        if (name.contains("가격")) return "여보세요, 네.";
        return "여보세요?";
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            stopAudio();
            setCallState("일시정지");
            setStatus("일시정지됨");
            pauseButton.setText("다시 시작");
        } else {
            pauseButton.setText("일시정지");
            setStatus("다시 듣기 시작");
            scheduleListening();
        }
    }

    private void scheduleListening() {
        if (paused || aiSpeaking) return;
        setCallState("듣는 중");
        setStatus("말씀하세요");
        handler.postDelayed(this::startSpeechRecognition, 1200);
    }

    private void startSpeechRecognition() {
        if (paused || aiSpeaking) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요.");
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000);
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            setStatus("음성인식 시작 실패: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                String text = (results != null && !results.isEmpty()) ? results.get(0) : "";
                if (text.trim().length() > 0) {
                    sendEmployeeMessage(text.trim());
                    return;
                }
            }
            if (!paused) scheduleListening();
        }
    }

    private void sendEmployeeMessage(String text) {
        setCallState("AI 생각 중");
        setStatus("AI 고객 답변 생성 중");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("session_id", sessionId);
                body.put("message", text);

                JSONObject res;
                try {
                    res = requestJson("/api/chat", "POST", body);
                } catch (Exception e) {
                    res = new JSONObject();
                    res.put("reply", fallbackReply(text));
                    res.put("use_openai_tts", false);
                    res.put("voice_style", "");
                }

                String reply = res.getString("reply");
                currentVoiceStyle = res.optString("voice_style", currentVoiceStyle);
                useOpenAiTts = res.optBoolean("use_openai_tts", false);
                speakAi(reply, true);
            } catch (Exception e) {
                setStatus("대화 오류: " + e.getMessage());
                scheduleListening();
            }
        }).start();
    }

    private String fallbackReply(String text) {
        String type = selectedCustomerTypeName();
        if (type.contains("가격")) {
            if (text.contains("자료") || text.contains("확인")) return "그러면 자료로 먼저 볼 수 있다는 말씀이세요?";
            if (text.contains("팀장")) return "팀장님이 짧게 설명해주실 수 있으면 들어볼게요.";
            return "근데 이거 비용이 드는 거죠? 대략 어느 정도인지 알아야 판단할 수 있어요.";
        }
        if (type.contains("의심")) {
            if (text.contains("자료") || text.contains("사례")) return "그럼 자료에 실제 확인할 수 있는 내용이 있나요?";
            return "혹시 광고대행 쪽인가요? 예전에 맡겼다가 효과를 못 봐서요.";
        }
        if (type.contains("바쁜")) {
            if (text.contains("20초") || text.contains("짧게")) return "네, 그럼 짧게만 말씀해보세요.";
            return "지금 바빠서요. 핵심만 짧게 말씀해주세요.";
        }
        return "네, 어떤 내용인지 조금 더 말씀해보세요.";
    }

    private void speakAi(String text, boolean listenAfter) {
        runOnUiThread(() -> {
            aiSpeaking = true;
            setCallState("AI 고객 말하는 중");
            setStatus("AI 고객 응답 중");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai_customer");
            int delay = Math.max(1800, Math.min(9000, text.length() * 140));
            handler.postDelayed(() -> {
                aiSpeaking = false;
                if (listenAfter && !paused) scheduleListening();
            }, delay);
        });
    }

    private void finishSession() {
        paused = true;
        stopAudio();
        long duration = Math.max(0, (System.currentTimeMillis() - callStartMillis) / 1000);
        setCallState("평가 중");
        setStatus("평가 리포트 생성 중");

        new Thread(() -> {
            try {
                if (sessionId != null && sessionId > 0) {
                    requestJson("/api/sessions/" + sessionId + "/finish", "POST", new JSONObject());
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "훈련 종료 / 통화시간 " + duration + "초", Toast.LENGTH_LONG).show();
                    setupPanel.setVisibility(View.VISIBLE);
                    callPanel.setVisibility(View.GONE);
                });
                setStatus(duration >= 180 ? "유효 훈련 완료" : "3분 미만 종료: 관리자 확인 대상");
            } catch (Exception e) {
                setStatus("평가 오류: " + e.getMessage());
                runOnUiThread(() -> {
                    setupPanel.setVisibility(View.VISIBLE);
                    callPanel.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void stopAudio() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
        try {
            if (tts != null) tts.stop();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
        if (tts != null) tts.shutdown();
    }
}
