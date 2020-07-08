package multiChatUi;

import static java.util.logging.Level.WARNING;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;
import com.google.gson.*;
//java object를 json으로 변환하거나 json을 java object로 반환하는데 사용하는 라이브러리

public class MultiChatController implements Runnable {

	// 뷰 클래스 참조 객체
	private final MultiChatUI v;

	// 데이터 클래스 참조 객체
	private final MultiChatData chatData;

	// 소켓 연결을 위한 변수 선언
	private String ip = "localhost"; //"203.252.148.148";
	private Socket socket;
	private BufferedReader inMsg = null;
	private PrintWriter outMsg = null;

	// 메시지 파싱을 위한 객체 생성
	Gson gson = new Gson();
	Message m;

	// 상태 플래그
	boolean status;

	// 로거 객체
	Logger logger;

	// 메시지 수신 스레드
	Thread thread;

	/**
	 * 모델과 뷰 객체를 파라미터로 하는 생성자
	 * 
	 * @param chatData
	 * @param v
	 */
	public MultiChatController(MultiChatData chatData, MultiChatUI v) {
		// 로거 객체 초기화
		logger = Logger.getLogger(this.getClass().getName());
		// MultiChatData, MultiChatUI 객체 초기화
		this.chatData = chatData;
		this.v = v;
	}

	/**
	 * 어플리케이션 메인 실행 메서드
	 */
	public void appMain() {
		// 데이터 객체(chatData)에서 (채팅내용 출력창의) 데이터 변화를 처리할 UI 객체 추가
		chatData.addObj(v.msgOut);
		
		v.addButtonActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Object obj = e.getSource();

				// 종료 버튼 처리
				if (obj == v.exitButton) {
					// java object => Json
					outMsg.println(gson.toJson(new Message(v.id, "", "", "logout")));

					// 종료 버튼 상태 : false
					status = false;

					if (outMsg != null)
						// 파일 쓰기 객체 닫기
						outMsg.close();

					try {
						if (inMsg != null) {
							// 파일 읽기 객체 닫기
							inMsg.close();
						}
					} catch (IOException ex) {
						ex.printStackTrace();
					}

					try {
						if (socket != null) {
							// 소켓 닫기
							socket.close();
						}
					} catch (IOException ex) {
						ex.printStackTrace();
					}

					try {
						// 다른 쓰레드의 종료를 기다린다.
						thread.join();
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
					// 시스템 종료
					System.exit(0);

				}
				// 로그인 버튼 처리
				else if (obj == v.loginButton) {
					// 사용자의 id 입력을 받아온다.
					String id = v.idInput.getText();
					
					// trim() : 앞 뒤 공백 제거
					// id 입력이 없는 경우 or id 앞 뒤 공백의 경우 logger 처리
					if (id == null || id.trim().equals("")) {
						logger.warning(
								"[MultiChatController]" + Thread.currentThread().getName() + " Exception 발생!! 2");
						return;
					}
					// id 앞 뒤 공백 제거
					v.id = id.trim();

					// outLabel에 출력 ( 대화명 : 박아무개 )
					v.outLabel.setText(" 대화명 : " + v.id);

					// 카드 레이아웃을 변경하여 로그인 상태(즉, 로그아웃이 보이게)로 전환
					v.cardLayout.show(v.tab, "logout");
					
					// 서버에 연결
					connectServer();
				}
				// 로그아웃 버튼 처리
				else if (obj == v.logoutButton) {
					// 로그아웃 메시지 전송
					outMsg.println(gson.toJson(new Message(v.id, "", "", "logout")));

					// 대화창 클리어
					v.msgOut.setText("");

					// 로그인 패널로 전환 및 소켓/스트림 닫기 + status 업데이트
					v.cardLayout.show(v.tab, "login");

					// 상태 전환 : false
					status = false;

					if (outMsg != null) {
						// 파일 쓰기 객체 닫기
						outMsg.close();
					}
					try {
						// 파일 읽기 객체 닫기
						inMsg.close();
					} catch (IOException ex) {
						// TODO Auto-generated catch block
						ex.printStackTrace();
					}

					try {
						if (socket != null) {
							// 소켓 닫기
							socket.close();
						}
					} catch (IOException ex) {
						// TODO Auto-generated catch block
						ex.printStackTrace();
					}

				}
				// 메시지 전송 버튼(엔터) 처리
				else if (obj == v.msgInput) {
					// 입력된 메시지 전송 (위의 로그아웃 메시지 전송코드와 Message 생성자 참고)
					outMsg.println(gson.toJson(new Message(v.id, "", v.msgInput.getText(), "msg")));
					// 입력창 클리어
					v.msgInput.setText(null);
				}
			}
		});
	}

	/**
	 * 서버 접속을 위한 메서드 (멤버필드를 적극 활용하기 바람; 이 메서드 안에서 새롭게 선언이 필요한 변수는 없음)
	 */
	public void connectServer() {
		try {
			// 소켓 생성 (ip, port는 임의로 설정하되 나중에 서버에서 듣게될 포트와 동일해야함)
			socket = new Socket(ip, 8888);

			// INFO 레벨 로깅 (서버 연결에 성공했다는 메시지 화면에 출력)
			logger.info(Thread.currentThread().getName() + " Server Connect Clear!!!");

			// 입출력(inMsg, outMsg) 스트림 생성
			inMsg = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			// 생성된 메세지 뒤에 이어쓰기 및 autoFlush
			outMsg = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

			// 서버에 로그인 메시지 전달
			outMsg.println(gson.toJson(new Message(v.id, "", "", "login")));

			// 메시지 수신을 위한 스레드(thread) 생성 및 스타트
			thread = new Thread(this, "Thread-START");
			thread.start();
		}
		// server에 연결을 못했을 경우 발생
		catch (Exception e) {
			logger.log(WARNING, "[MultiChatUI]connectServer() Exception 발생!!");
			status = false;
			v.cardLayout.show(v.tab, "login");
			v.msgOut.setText("Cannot Connect to Server...");
			e.printStackTrace();

		}
	}

	/**
	 * 메시지 수신을 독립적으로 처리하기 위한 스레드 실행
	 */
	public void run() {
		// 수신 메시지 처리를 위한 변수
		String msg = null;

		// currentThread() 현재
		logger.info(Thread.currentThread().getName() + "Message stream START!!!");

		// status 업데이트
		status = true;

		while (status) {
			try {
				// 메시지 수신
				msg = inMsg.readLine();

				// 메시지 파싱
				m = gson.fromJson(msg, Message.class);

				// MultiChatData 객체를 통해 데이터 갱신
				chatData.refreshData(m.getId() + ">" + m.getMsg() + "\n");

				// 커서를 현재 대화 메시지에 보여줌
				// setCaretPosition : TextComponent 의 텍스트 삽입 caret의 위치를 설정
				// caret : 문서보기 내의 위치? 문서에서 점이라고 하는 위치를 가진다.
				// 제일 뒤에 가져다 붙여진다.
				v.msgOut.setCaretPosition(v.msgOut.getDocument().getLength());
			} catch (IOException e) {
				logger.log(WARNING, "[MultiChatUI]메시지 스트림 종료!!");
			}
		}
		logger.info("[MultiChatUI]" + thread.getName() + " Message Reception Thread Termination!");
	}

	// 프로그램 시작을 위한 메인 메서드
	public static void main(String[] args) {
		// MultiChatController 객체생성 및 appMain() 실행
		MultiChatData chatData = new MultiChatData();
		MultiChatUI v = new MultiChatUI();
		MultiChatController chatController = new MultiChatController(chatData, v);
		chatController.appMain();
	}
}