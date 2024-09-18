package com.deliveryn.orderlist;

import android.content.Intent;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OrderDetailActivity extends AppCompatActivity {

    private OrderRoom selectedRoom;
    private Button participateButton, orderStatusButton, chattingButton, removeRoomButton, completeButton;
    private EditText delMenu, delOption, delPrice;
    private String userId, roomId, boss, order, bossName;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.order_room_page);

        initViews();
        getSelectedRoom();
        getBossName();
        setValues();

        removeRoomButton.setOnClickListener(v -> showRemoveRoomDialog());
        participateButton.setOnClickListener(v -> participation());
        chattingButton.setOnClickListener(v -> chatting());
        orderStatusButton.setOnClickListener(v -> orderStatus());
        completeButton.setOnClickListener(v -> completeDelivery());
    }

    private void initViews() {
        participateButton = findViewById(R.id.participate);
        orderStatusButton = findViewById(R.id.ord_status);
        chattingButton = findViewById(R.id.chatting);
        removeRoomButton = findViewById(R.id.remove_room);
        completeButton = findViewById(R.id.complete_deliver);
    }

    private void getSelectedRoom() {
        Intent intent = getIntent();
        roomId = intent.getStringExtra("roomId");
        String resName = intent.getStringExtra("resName");
        String resCategory = intent.getStringExtra("resCategory");
        String deliverTime = intent.getStringExtra("deliverTime");
        String deliverLocation = intent.getStringExtra("deliverLocation");
        String deliverLink = intent.getStringExtra("deliverLink");
        selectedRoom = new OrderRoom(roomId, resName, resCategory, deliverTime, deliverLocation, deliverLink);
        userId = intent.getStringExtra("participate");
    }

    private void getBossName() {
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(selectedRoom.roomId).child("boss").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("Firebase", "Error getting data", task.getException());
                    } else {
                        boss = String.valueOf(task.getResult().getValue());
                        Log.d("boss id: ", boss);
                        FirebaseDatabase.getInstance().getReference().child("users").child(boss).child("nickname").get()
                                .addOnCompleteListener(task1 -> {
                                    if (!task1.isSuccessful()) {
                                        Log.e("Firebase", "Error getting data", task1.getException());
                                    } else {
                                        bossName = String.valueOf(task1.getResult().getValue());
                                        Log.d("boss Nick Name: ", bossName);
                                        TextView bossNickName = findViewById(R.id.boss);
                                        bossNickName.setText("방장: " + bossName);
                                    }
                                });
                    }
                });
    }

    private void setValues() {
        TextView restName = findViewById(R.id.r_name);
        TextView resCategory = findViewById(R.id.r_category);
        TextView ordTime = findViewById(R.id.od_time);
        TextView deliverLocation = findViewById(R.id.del_loc);
        TextView deliverLink = findViewById(R.id.del_link);

        deliverLink.setAutoLinkMask(Linkify.WEB_URLS); // 가게 링크 연결

        restName.setText(selectedRoom.resName);
        resCategory.setText("카테고리: " + selectedRoom.resCategory);
        ordTime.setText("주문 시간: " + selectedRoom.deliverTime);
        deliverLocation.setText("배달 장소: " + selectedRoom.deliverLocation);
        deliverLink.setText("가게 링크: " + selectedRoom.deliverLink);
    }

    // 삭제하기
    private void showRemoveRoomDialog() {
        View removeView = View.inflate(this, R.layout.remove_room, null);
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("주문 방 삭제")
                .setView(removeView)
                .setPositiveButton("삭제", (dialog, which) -> removeRoom())
                .setNegativeButton("취소", null)
                .show();
    }

    private void removeRoom() {
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("boss").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("firebase", "Error getting data", task.getException());
                    } else {
                        // boss uid를 변수 boss에 저장
                        boss = String.valueOf(task.getResult().getValue());

                        // 현재 로그인한 uid와 boss의 value(uid)를 비교 -> 방장이면 주문 방을 삭제할 수 있음
                        if (boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                            FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        FirebaseDatabase.getInstance().getReference().child("Massages").child(roomId).removeValue();
                                        Toast.makeText(OrderDetailActivity.this, "주문 방 삭제 성공", Toast.LENGTH_SHORT).show();
                                        finish();
                                    });
                        } else {
                            // 방장이 아닌 user가 삭제하기 버튼을 클릭 -> 주문 방 삭제 불가
                            Toast.makeText(OrderDetailActivity.this, "삭제 권한이 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // 참여하기
    private void participation() {
        // 방장 정보 가져오기
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("boss").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("firebase", "Error getting data", task.getException());
                    } else {
                        boss = String.valueOf(task.getResult().getValue());

                        // 참여하고 있는 user인지 체크
                        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(
                                new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        boolean isParticipating = snapshot.hasChild(FirebaseAuth.getInstance().getCurrentUser().getUid());

                                        if (boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                                            Toast.makeText(OrderDetailActivity.this, "방장은 누를 수 없습니다.", Toast.LENGTH_SHORT).show();
                                        } else if (isParticipating) {
                                            Toast.makeText(OrderDetailActivity.this, "이미 참여하고 있습니다.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            showParticipationDialog();
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                    }
                                });
                    }
                });
    }

    private void showParticipationDialog() {
        View participateView = View.inflate(this, R.layout.participate_user, null);new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setView(participateView)
                .setPositiveButton("확인", (dialog, which) -> submitParticipation(participateView))
                .setNegativeButton("취소", null)
                .show();
    }

    private void submitParticipation(View participateView) {
        delMenu = participateView.findViewById(R.id.del_menu);
        delOption = participateView.findViewById(R.id.del_option);
        delPrice = participateView.findViewById(R.id.del_price);

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseDatabase.getInstance().getReference().child("users").child(firebaseUser.getUid()).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        OrderInfoModel order = new OrderInfoModel();
                        UserModel user = dataSnapshot.getValue(UserModel.class);
                        order.setNickname(user.getNickname());
                        order.setMenu(delMenu.getText().toString());
                        order.setOption(delOption.getText().toString());
                        order.setPrice(delPrice.getText().toString());
                        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("users").child(firebaseUser.getUid()).setValue(order);
                        Toast.makeText(OrderDetailActivity.this, "입력 완료", Toast.LENGTH_SHORT).show();
                        updateOrderNum();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(OrderDetailActivity.this, "error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 인원 수 1 증가
    private void updateOrderNum() {
        database.getReference("orderlist").child(roomId).child("orderNum").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int value = snapshot.getValue(Integer.class); //인원 수 가져옴
                        database.getReference("orderlist").child(roomId).child("orderNum").setValue(value + 1); //인원 수 1 증가 저장
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    // 채팅하기
    private void chatting() {
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("boss").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("firebase", "Error getting data", task.getException());
                    } else {
                        boss = String.valueOf(task.getResult().getValue());
                        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(
                                new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        if (boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())
                                                || snapshot.hasChild(userId)) {
                                            Intent chatIntent = new Intent(OrderDetailActivity.this, ChatActivity.class);
                                            chatIntent.putExtra("roomId", roomId);
                                            startActivity(chatIntent);
                                        } else {
                                            Toast.makeText(OrderDetailActivity.this, "권한이 없습니다!", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                    }
                                });
                    }
                });
    }

    //주문 현황
    private void orderStatus() {
        // 방장이 배달비까지 입력했는지 확인
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("orderState").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("firebase", "Error getting data", task.getException());
                    } else {
                        order = String.valueOf(task.getResult().getValue());

                        // 방장이 배달비를 입력하지 않은 경우 OrderStatus1으로 이동
                        // 방장이 배달비를 입력한 경우 OrderStatus2로 이동
                        Class<?> targetClass = order.equals("before") ? OrderStatus1.class : OrderStatus2.class;

                        Intent intent = new Intent(OrderDetailActivity.this, targetClass);
                        intent.putExtra("roomId", roomId);
                        startActivity(intent);
                    }
                });
    }

    // 배달 완료
    private void completeDelivery() {
        // 방장 정보 가져오기
        FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("boss").get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("firebase", "Error getting data", task.getException());
                    } else {
                        // 참여하고 있는 user인지 체크
                        boss = String.valueOf(task.getResult().getValue());

                        // 배달 완료 push 알림
                        // 방장이 클릭한 경우
                        if (boss.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                            FirebaseDatabase.getInstance().getReference().child("orderlist").child(roomId).child("users").addListenerForSingleValueEvent(
                                    new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                            Map<String, Boolean> participationUsers = (Map<String, Boolean>) snapshot.getValue();

                                            if (participationUsers == null || participationUsers.isEmpty()) {
                                                Toast.makeText(OrderDetailActivity.this, "참여자가 없습니다.", Toast.LENGTH_SHORT).show();
                                            } else {
                                                for (String item : participationUsers.keySet()) {
                                                    database.getReference().child("users").child(item).child("pushToken").get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                                                        @Override
                                                        public void onComplete(@NonNull Task<DataSnapshot> task) {
                                                            if (!task.isSuccessful()) {
                                                                Log.e("firebase", "Error getting data", task.getException());
                                                            } else {
                                                                String pushToken = String.valueOf(task.getResult().getValue());
                                                                sendFCM(pushToken);
                                                                Toast.makeText(OrderDetailActivity.this, "참여자들에게 푸시 알림을 보냈습니다.", Toast.LENGTH_SHORT).show();
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                        }
                                    });
                        }

                        // 방장이 아닌 user가 클릭한 경우
                        else {
                            Toast.makeText(OrderDetailActivity.this, "권한이 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendFCM(String pushToken) {
        Gson gson = new Gson();

        NotificationModel notificationModel = new NotificationModel();
        notificationModel.to = pushToken;
        notificationModel.data.title = "배달 완료!";
        notificationModel.data.text = "배달 장소로 와주시길 바랍니다.";

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
                Log.e("push 알림: ", "실패", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d("push 알림: ", "성공: " + response.body().string());
                } else {
                    Log.e("push 알림: ", "실패: " + response.message());
                }
            }
        });
    }
}