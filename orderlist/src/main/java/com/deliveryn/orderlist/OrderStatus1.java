package com.deliveryn.orderlist;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OrderStatus1 extends AppCompatActivity {

    RecyclerView recyclerView;
    RecyclerView.Adapter adapter;
    ArrayList<OrderInfoModel> orderList;
    FirebaseDatabase database;
    Button orderBtn, reportBtn1;
    View dialogView;
    String boss, roomId, reportRadioBtnValue;
    Spinner reportType;
    int flag = 0, peopleNum;
    RadioGroup radioGroup;
    Double deliveryFee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_status1);

        initViews();
        setupRecyclerView();
        loadData();

        orderBtn.setOnClickListener(view -> orderButtonClick());
        reportBtn1.setOnClickListener(view -> showReportDialog());
    }

    private void initViews() {
        orderBtn = findViewById(R.id.orderBtn);
        reportBtn1 = findViewById(R.id.reportBtn1);
        recyclerView = findViewById(R.id.recyclerView1);
        database = FirebaseDatabase.getInstance();

        Intent intent = getIntent();
        roomId = intent.getStringExtra("roomId");
    }

    private void setupRecyclerView() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        orderList = new ArrayList<>();
        adapter = new CustomAdapter1(orderList, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        database.getReference("orderlist").child(roomId).child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot datasnapshot) {
                orderList.clear(); //기존 배열 리스트가 존재하지 않게 초기화
                for (DataSnapshot snapshot : datasnapshot.getChildren()) {
                    OrderInfoModel orderInfo = snapshot.getValue(OrderInfoModel.class);
                    if (orderInfo != null) {
                        orderInfo.setMenu(snapshot.child("menu").getValue(String.class));
                        orderInfo.setOption(snapshot.child("option").getValue(String.class));
                        orderInfo.setPrice(snapshot.child("price").getValue(String.class));
                        orderInfo.setNickname(snapshot.child("nickname").getValue(String.class));
                        orderList.add(orderInfo);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", error.getMessage());
            }
        });

        // 방장 정보 가져오기
        database.getReference("orderlist").child(roomId).child("boss").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                boss = String.valueOf(task.getResult().getValue());
            } else {
                Log.e("firebase", "Error getting data", task.getException());
            }
        });
    }

    private void orderButtonClick() {
        if (!boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            Toast.makeText(this, "권한이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 참여자 있는지 확인
        database.getReference("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(OrderStatus1.this, "참여자가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                dialogView = View.inflate(OrderStatus1.this, R.layout.order_menu, null);
                AlertDialog.Builder dlg = new AlertDialog.Builder(OrderStatus1.this, R.style.MyAlertDialogStyle);
                dlg.setView(dialogView)
                        .setPositiveButton("확인", (dialog, which) -> confirmOrder(dialogView))
                        .setNegativeButton("취소", null)
                        .show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", error.getMessage());
            }
        });
    }

    private void confirmOrder(View dialogView) {
        EditText edtDeliveryFee = dialogView.findViewById(R.id.edt_delivery_fee);
        EditText edtAccountNumber = dialogView.findViewById(R.id.edt_account_number);
        EditText edtBankName = dialogView.findViewById(R.id.edt_bank_name);

        // 총 배달비
        deliveryFee = Double.parseDouble(edtDeliveryFee.getText().toString());

        Map<String, Object> updates = new HashMap<>();
        updates.put("delivery fee", edtDeliveryFee.getText().toString());
        updates.put("bank", edtBankName.getText().toString());
        updates.put("account number", edtAccountNumber.getText().toString());
        updates.put("orderState", "after"); //파이어베이스 실시간 데이터베이스에 주문 여부 before->after로 변경
        database.getReference("orderlist").child(roomId).updateChildren(updates);

        calculatePerDeliveryFee();

        notifyUsers();
    }

    private void calculatePerDeliveryFee() {
        database.getReference("orderlist").child(roomId).child("orderNum").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                peopleNum = Integer.parseInt(String.valueOf(task.getResult().getValue()));

                // 1인당 배달비(소수점 첫 번째 자리에서 올림)
                int perDeliveryFee = (int) Math.ceil(deliveryFee / peopleNum);
                database.getReference("orderlist").child(roomId).updateChildren(Map.of("per delivery fee", perDeliveryFee));
            } else {
                Log.e("firebase", "Error getting data", task.getException());
            }
        });
    }

    // 주문하기 push 알림
    private void notifyUsers() {
        database.getReference("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (userId != null) {
                        database.getReference("users").child(userId).child("pushToken").get().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                sendFCM(String.valueOf(task.getResult().getValue()));
                            } else {
                                Log.e("firebase", "Error getting data", task.getException());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FirebaseError", error.getMessage());
            }
        });

        // 총 금액 나오는 주문 현황 액티비티로 전환
        Intent roomIdIntent = new Intent(OrderStatus1.this, OrderStatus2.class);
        roomIdIntent.putExtra("roomId", roomId);
        startActivity(roomIdIntent);
        finish();
    }

    private void sendFCM(String pushToken) {
        Gson gson = new Gson();
        NotificationModel notificationModel = new NotificationModel();
        notificationModel.to = pushToken;
        notificationModel.data.title = "입금 요청!";
        notificationModel.data.text = "방장이 주문을 하려고 합니다.";

        RequestBody requestBody = RequestBody.create(gson.toJson(notificationModel), MediaType.parse("application/json; charset=utf8"));
        Request request = new Request.Builder()
                .header("Content-Type", "application/json")
                .addHeader("Authorization", BuildConfig.FCM_KEY)
                .url("https://fcm.googleapis.com/fcm/send")
                .post(requestBody)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("push 알림: ", "실패");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
            }
        });
    }

    private void showReportDialog() {
        // 다이얼로그 뷰 inflate
        dialogView = View.inflate(OrderStatus1.this, R.layout.report_dialog, null);
        AlertDialog.Builder dlg = new AlertDialog.Builder(OrderStatus1.this, R.style.MyAlertDialogStyle);
        dlg.setTitle("           <신고할 사용자 정보 입력>");
        dlg.setView(dialogView);

        radioGroup = dialogView.findViewById(R.id.radioGroup);
        LinearLayout linearLayoutReportReasonVisibility = dialogView.findViewById(R.id.linearLayout_report_reason);
        EditText reportReason = dialogView.findViewById(R.id.report_reason);
        EditText reportNickname = dialogView.findViewById(R.id.report_nickname);

        // 신고 유형 - 기타 누르면 신고 사유 visible
        reportType = dialogView.findViewById(R.id.report_list);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(OrderStatus1.this, R.array.report_spinner_array, android.R.layout.simple_spinner_dropdown_item);

        // 드롭다운 클릭 시 선택 창
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 스피너에 어댑터 설정
        reportType.setAdapter(adapter);

        // 스피너 항목 선택 시 처리
        reportType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 신고 유형 - 기타 눌렀을 경우
                if (reportType.getSelectedItem().toString().equals("기타")) {
                    flag = 1;
                    linearLayoutReportReasonVisibility.setVisibility(View.VISIBLE);
                } else {
                    flag = 0;
                    linearLayoutReportReasonVisibility.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                flag = 0;
            }
        });

        // 라디오 버튼 선택 시 처리
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.report_radioBtn_boss) {
                reportRadioBtnValue = "방장";
            } else {
                reportRadioBtnValue = "참여자";
            }
        });

        // 확인 버튼 클릭 시 처리
        dlg.setPositiveButton("확인", (dialog, which) -> {
            // 신고할 닉네임
            String reportNicknameValue = reportNickname.getText().toString();

            // 신고 유형
            String typeValue = reportType.getSelectedItem().toString();

            // 신고하는 계정 닉네임(현재 사용자)
            String reporter = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();

            // 현재 날짜 및 시간
            Object datetime = ServerValue.TIMESTAMP;

            // 신고 사유 있는 경우(= 신고 유형이 기타인 경우)
            // 신고 사유 없는 경우(= 신고 유형이 기타가 아닌 경우)
            String reportReasonValue = flag == 1 ? reportReason.getText().toString() : null;

            insertReport(reporter, datetime, reportRadioBtnValue, reportNicknameValue, typeValue, reportReasonValue);

            Toast.makeText(OrderStatus1.this, "신고되었습니다.", Toast.LENGTH_SHORT).show();
        });

        dlg.setNegativeButton("취소", null);
        dlg.show();
    }

    public void insertReport(String reporter, Object datetime, String reportedType, String reportedNickname, String reportType, String specific) {
        // ReportModel 객체를 생성할 때 specific 값이 null 또는 빈 문자열이 아니면 설정
        ReportModel reportModel;
        if (specific != null && !specific.isEmpty()) {
            reportModel = new ReportModel(reporter, datetime, reportedType, reportedNickname, reportType, specific);
        } else {
            reportModel = new ReportModel(reporter, datetime, reportedType, reportedNickname, reportType);
        }

        DatabaseReference pushRef = database.getReference("report").push();
        pushRef.setValue(reportModel);
    }
}