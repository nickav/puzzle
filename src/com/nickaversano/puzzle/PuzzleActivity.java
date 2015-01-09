package com.nickaversano.puzzle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.nickaversano.puzzle.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
 
public class PuzzleActivity extends Activity {
	
	private PuzzleView game;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        game = new PuzzleView(this);
        openMenu(); 
    }
 
    class PuzzleView extends SurfaceView implements SurfaceHolder.Callback
    {
    	private Bitmap resizedImage;
    	private AppThread thread;
    	private Bitmap[] images;
    	private int[] positions, initialPositions;
    	private int num;
    	private int TOTAL_SPACES;
    	private int EMPTY_SPACE;
    	private int xSize, ySize;
    	private float xSpace, ySpace, initXSpace, initYSpace;
    	public float x;
    	private final float PERCENT_PADDING = .01f;
    	private int padding;
    	private float xOffset, yOffset, initXOffset, initYOffset, finXOffset, finYOffset;
    	private int deviceWidth, deviceHeight;
    	private int dontDraw = -1;
    	private int moveFrom, moveTo;
    	private float time;
    	
    	private final float DEFAULT_ANIMATION_TIME = 15, FAST_ANIMATION_TIME = 5;
    	private float ANIMATION_TIME = DEFAULT_ANIMATION_TIME;
    	private float anchorX, anchorY;
    	private float[] startPos, endPos;
    	private boolean moveFast;
    	
    	private boolean movingPiece;
    	private int state;
    	private static final int STATE_INIT = 0, STATE_SHUFFLE = 1, STATE_ACTIVE = 2, STATE_WAIT = 3, STATE_WIN = 4;
    	private int shuffleTimes;
    	private long startTime;
    	private final long WAIT_TIME = 1000;
    	private final float WIN_ANIMATION_TIME = 1000f;
    	private float amt;
    	private long initWinTime;
    	private int moves;
    	private long firstMoveTime;
    	private boolean didMove;
    	private String winMessage;
    	private boolean didShowWinMessage;
    	private boolean didActivate;
    	
    	private boolean showNumbers;
    	
    	private boolean changeOrientation;
    	
    	//settings
    	private static final String SETTING_DIFF = "Diffuculty", SETTING_NUMBERS = "ShowNumbers";
    	private static final int SETTING_DIFF_DEFAULT = 3;
    	private static final boolean SETTING_NUMBERS_DEFAULT = false;
    	
        public PuzzleView(Context context) {
            super(context);
            SurfaceHolder holder = getHolder();
            holder.addCallback(this);
            thread = new AppThread(holder, this);
            
            load();
        }
        
        public void load() {
        	SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            num = settings.getInt(SETTING_DIFF, SETTING_DIFF_DEFAULT);
            showNumbers = settings.getBoolean(SETTING_NUMBERS, SETTING_NUMBERS_DEFAULT);
        }
        
        public void save() {
        	SharedPreferences.Editor settings = getPreferences(Context.MODE_PRIVATE).edit();
        	settings.putInt(SETTING_DIFF, num);
        	settings.putBoolean(SETTING_NUMBERS, showNumbers);
        	settings.commit();
        }
        
        public void init(Bitmap originalImage) {
        	state = STATE_INIT;
        	
        	TOTAL_SPACES = num*num;
        	images = new Bitmap[TOTAL_SPACES];
        	EMPTY_SPACE = TOTAL_SPACES - 1;
        	
        	positions = new int[TOTAL_SPACES];
        	for (int k=0; k<TOTAL_SPACES; k++) {
        		positions[k] = k;
        	}
        	
        	initialPositions = new int[TOTAL_SPACES];
        	
        	int imageWidth = originalImage.getWidth(), imageHeight = originalImage.getHeight();
        	Display display = getWindowManager().getDefaultDisplay(); 
        	int newWidth = deviceWidth = display.getWidth();
        	int newHeight = deviceHeight = display.getHeight();
        	
        	if (imageHeight > imageWidth && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            	//keep landscape
            	//matrix.setRotate(-90f, w/2, h/2);
            	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            	changeOrientation = true;
            	resizedImage = originalImage;
            	return;
            }
        	int deviceSize = deviceWidth;
        	if (deviceWidth < deviceHeight) deviceSize = deviceHeight;
        	padding = (int)(PERCENT_PADDING*((float)deviceSize));
        	int imagePadding = padding*(num+2);
        	newWidth = newWidth - imagePadding;
        	newHeight = newHeight - imagePadding;
            
            float sourceRatio = (float)imageWidth/imageHeight, destRatio = (float)deviceWidth/deviceHeight;
            
            float scaleWidth = (float) newWidth / imageWidth;
            float scaleHeight = (float) newHeight / imageHeight;
            
            Matrix matrix = new Matrix();
            
            int translatex = 0, translatey = 0;
            
            if (sourceRatio + .1f < destRatio) {
            	//fit width
            	float nw = deviceWidth;
            	float nh = (int)(nw/sourceRatio) - imagePadding;
            	scaleHeight = nh / imageHeight;
            	translatey = (int)(nh-deviceHeight)/2;
            	Log.i("TRANSLATE", "y : " + translatey);
            	Log.i("VARS", "imageWidth: " + imageHeight + " scaleWidth: " + scaleHeight + " transx " + translatey + " nw: " + nh + " devHeight: " + deviceHeight);
            	if (translatey<0) translatey = 0;

            	Log.i("FIT", "FIT WIDTH");
            }
            else if (sourceRatio - .1f > destRatio){
            	//fit height
            	float nh = deviceHeight;
            	float nw = (int)(nh*sourceRatio) - imagePadding;
            	Log.i("new width", nw + "");
            	scaleWidth = nw / imageWidth;
            	translatex = (int)(nw-deviceWidth)/2;
            	Log.i("TRANSLATE", "x : " + translatex);
            	if (translatex<0) translatex = 0;
            	
            	Log.i("FIT", "FIT HEIGHT");
            }
            
            matrix.postScale(scaleWidth, scaleHeight);
            matrix.postTranslate(-100f, -100f);
            
            resizedImage = Bitmap.createBitmap(originalImage, 0, 0, imageWidth, imageHeight, matrix, true);
            
            xSize = (int)(newWidth)/num;
        	ySize = (int)(newHeight)/num;
            
        	xSpace = initXSpace = xSize + padding;
        	ySpace = initYSpace = ySize + padding;
        	xOffset = initXOffset = ((float)deviceWidth - ((float)newWidth + padding*(num-1)))/2f;
        	yOffset = initYOffset = ((float)deviceHeight - ((float)newHeight + padding*(num-1)))/2f;
        	finXOffset = ((float)deviceWidth - xSize*num)/2f;
        	finYOffset = ((float)deviceWidth - xSize*num)/2f;
        	
        	//j is x, i is y
        	for (int i=0; i<num; i++)
        		for (int j=0; j<num; j++) {
        			images[i*num + j] = Bitmap.createBitmap(resizedImage, translatex + j*xSize, translatey + i*ySize, xSize, ySize);
        		}
        	
        	originalImage.recycle();
        	
        	didActivate = false;
        	
        	movingPiece = false;
			time = 0;
			dontDraw = -1;
			amt = 0;
			didMove = false;
			moves = 0;
			didShowWinMessage = false;
        	
        	shuffle();
        }
        
        public void setDifficulty(int size) {
        	synchronized (this) {
	        	num = size;
	        	if (resizedImage!=null) init(resizedImage);
        	}
        }

        public void update() {
        	if (state==STATE_WAIT) {
        		if (System.currentTimeMillis() - startTime>WAIT_TIME)
        			state = STATE_SHUFFLE;
        	}
        	if (state==STATE_SHUFFLE) {
        		if (shuffleTimes>0) {
        			if (!movingPiece) {
        				List<Integer> spaces = new ArrayList<Integer>();
        				int i = moveFrom/num;
        				int j = moveFrom - i*num;
        				
        				if (i>0) spaces.add((i-1)*num + j);
		        		if (i<num-1) spaces.add((i+1)*num + j);
		        		if (j>0) spaces.add(i*num + j - 1);
		        		if (j<num-1) spaces.add(i*num + j + 1);

		        		for (int k=0, n = spaces.size(); k<n; k++)
		        			if (spaces.get(k)==moveTo) {
		        				spaces.remove(k);
		        				break;
		        			}

		        		int n = (int)(Math.random()*spaces.size());
		        		movePiece(spaces.get(n),  moveFrom);
		        		shuffleTimes --;
        			}
        		} else {
        			for (int i=0; i<num; i++)
        				for (int j=0; j<num; j++)
        					initialPositions[i*num + j] = positions[i*num + j];
        			if (movingPiece) {
	        			initialPositions[moveTo] = initialPositions[moveFrom];
	        			initialPositions[moveFrom] = EMPTY_SPACE;
        			}
        			
        			ANIMATION_TIME = DEFAULT_ANIMATION_TIME;
        			state = STATE_ACTIVE;
        		}
        	}
        	
        	if (state==STATE_ACTIVE && state!=STATE_WIN) {
        		if (positions[0]==0 && positions[EMPTY_SPACE]==EMPTY_SPACE) {
        			boolean complete = true;
        			loop:
        			for (int i=0; i<num; i++)
        				for (int j=0; j<num; j++) {
        					int index = i*num + j;
        					if (positions[index]!=index) {
        						complete = false;
        						break loop;
        					}
        				}
        			if (complete) {
        				state = STATE_WIN;
        				initWinTime = System.currentTimeMillis();
        				xSpace -= padding;
        				ySpace -= padding;
        				
        				float gameTime = (Math.round((initWinTime - firstMoveTime)/10f))/100f;
        				winMessage = gameTime + " seconds with " + (moves-1) + " moves";
        			}
        		}
        	}
        	
        	if (state==STATE_WIN) {
        		float delta = System.currentTimeMillis() - initWinTime;
        		amt = Math.min(delta/(WIN_ANIMATION_TIME), 1f);
        		xOffset = initXOffset + (finXOffset - initXOffset)*amt;
        		yOffset = initYOffset + (finYOffset - initYOffset)*amt;
        		xSpace = initXSpace - ((float)padding)*amt;
        		ySpace = initYSpace - ((float)padding)*amt;
        		
        		if (delta>2*WIN_ANIMATION_TIME) {
        			if (!didShowWinMessage) {
	        			//win();
	        			didShowWinMessage = true;
	        		}
        		}
        	}
        }
        
        @Override
        public void onDraw(Canvas canvas)
        {
        	synchronized (this) {
	        	canvas.drawColor(Color.WHITE);
	        	if (state!=STATE_INIT)
	        	{
	        		Paint textPaint = null;
	        		if (showNumbers) {
	        			textPaint = new Paint();
		                textPaint.setColor(Color.WHITE);
		                textPaint.setTextAlign(Paint.Align.RIGHT);
		                textPaint.setTextSize((int)(ySize/3));
		                textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		                textPaint.setShadowLayer(4f, 0f, 0f, Color.BLACK);
	        		}
	        		
	        		for (int i=0; i<num; i++)
	            		for (int j=0; j<num; j++) {
	            			int index = positions[i*num + j];
	            			if ((index<EMPTY_SPACE || state==STATE_WIN) && index!=dontDraw) {
	            				Paint paint = null;
	            				if (state==STATE_WIN && index==EMPTY_SPACE) {
	            					paint = new Paint();
	            					paint.setAlpha((int)(255*amt));
	            				}
	            				
	            				if (images[index]!=null) canvas.drawBitmap(images[index], xOffset + j*xSpace, yOffset + i*ySpace, paint);
	            				
	        	        		if (showNumbers && textPaint!=null && state!=STATE_WIN) canvas.drawText((index+1) + "", j*xSpace + xSpace, i*ySpace + ySpace, textPaint);
	            			}
	            		}
	        		
	        		if (movingPiece) {
	        			int imageIndex = positions[moveFrom];
	        			time = (time<ANIMATION_TIME)?time:ANIMATION_TIME;
	        			float p = (1-(ANIMATION_TIME+1)/((time+1)*ANIMATION_TIME)) + 0.1f;
	        			float x = startPos[0] + (endPos[0]-startPos[0])*p, y = startPos[1] + p*(endPos[1]-startPos[1]);
	        			
	        			if (images[imageIndex]!=null) canvas.drawBitmap(images[imageIndex], x, y, null);
	        			if (showNumbers && textPaint!=null && state!=STATE_WIN) canvas.drawText((imageIndex+1) + "", x + xSpace - xOffset, y + ySpace - yOffset, textPaint);
	        			
	        			if (moveFast) time+=4;
	        			if (time ++>ANIMATION_TIME) {
	        				movingPiece = false;
	        				time = 0;
	        				dontDraw = -1;
	        				swap(moveFrom, moveTo);
	        			}
	        		}
	        	}
	        	//if (resizedImage!=null) canvas.drawBitmap(resizedImage, 0, 0, null);
        	}
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event)
        {
        	float touchX = event.getX(), touchY = event.getY();
        	switch (event.getAction()) {
            	case MotionEvent.ACTION_DOWN:
		        	if (!movingPiece && state==STATE_ACTIVE) {
		        		moveFast = false;
		        		anchorX = touchX;
		        		anchorY = touchY;
			        	int j = (int)((touchX-xOffset)/xSpace);
			        	int i = (int)((touchY-yOffset)/ySpace);
			        	
			        	int index = i*num + j;
			        	if (index>=0 && index<TOTAL_SPACES) {
				        	int n = positions[index];
				        	
				        	if (n!=EMPTY_SPACE) {
				        		int possibles[] = new int[4];
				        		for (int k=0; k<4; k++) possibles[k] = -1;
				        		
				        		if (i>0) possibles[0] = (i-1)*num + j;
				        		if (i<num-1) possibles[1] = (i+1)*num + j;
				        		if (j>0) possibles[2] = i*num + j - 1;
				        		if (j<num-1) possibles[3] = i*num + j + 1;
				        		
				        		for (int k=0; k<4; k++) {
				        			int posIndex = possibles[k];
				        			if (posIndex>=0 && posIndex<TOTAL_SPACES) {
					        			int p = positions[posIndex];
					        			if (p==EMPTY_SPACE) {
					        				movePiece(index, posIndex);
					        			}
				        			}
				        		}
				        	}
			        	}
		        	}
		        	if (state==STATE_WIN) {
		        		win();
		        	}
		        	break;
            	case MotionEvent.ACTION_MOVE:
            		if (movingPiece) {
	            		float dx = Math.abs(touchX - anchorX), dy = Math.abs(touchY - anchorY);
	            		anchorX = touchX;
	            		anchorY = touchY;
	            		float influence = 5;
	            		float dist = dx/(endPos[0]-startPos[0]);
	            		if ((endPos[0]-startPos[0])==0) {
	            			dist = dy/(endPos[1]-startPos[1]);
	            		}
	            		
	            		time += influence*Math.abs(dist);
            		}
            		break;
            	case MotionEvent.ACTION_UP:
            		//movingPiece = true;
            		moveFast = true;
            		break;
        	}
        	return true;
        }
        
        private void shuffle() {
        	state = STATE_WAIT;
        	shuffleTimes = 2*num*num*num;
        	ANIMATION_TIME = FAST_ANIMATION_TIME;
        	moveTo = -1;
        	moveFrom = EMPTY_SPACE;
        	moveFast = true;
        	startTime = System.currentTimeMillis();
        }
        
        private void movePiece(int from, int to) {
        	movingPiece = true;
			moveFrom = from;
			moveTo = to;
			startPos = getIndexPosition(moveFrom);
			endPos = getIndexPosition(moveTo);
			dontDraw = positions[from];
        }
        
        private void swap(int from, int to) {
        	positions[to] = positions[from];
			positions[from] = EMPTY_SPACE;
			
			if (state==STATE_ACTIVE) {
				moves ++;
				if (!didMove) {
					didMove = true;
					firstMoveTime = System.currentTimeMillis();
				}
			}
        }
        
        private float[] getIndexPosition(int index) {
        	float result[] = new float[2];
        	result[1] = index/num;
        	result[0] = index - result[1]*num;
        	result[0] = result[0]*xSpace + xOffset;
        	result[1] = result[1]*ySpace + yOffset;
        	return result;
        }
        
        private void win() {
        	PuzzleActivity app = (PuzzleActivity)getContext();
        	app.alert("Puzzle Completed!", "In " + winMessage + "!");
        }
        
        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
        	if (!thread.isRunning()) {
	        	thread.setRunning(true);
	        	thread.start();
        	}
        	else unpause();
        }
        
        public void pause() {
        	thread.pause();
        }
        
        public void unpause() {
        	if (thread.isPaused()){
        		thread.unpause();
        	}
        }
        
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
        	Log.i("SURFACE CHANGED", "SURFACE CHANGED");
        	if (changeOrientation) {
        		this.init(resizedImage);
        		changeOrientation = false;
        	}
        }
        
        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {
        	pause();
        }
        
        //cleans puzzle
        public void freeImages() {
        	for (int i=0, n=images.length; i<n; i++) {
        		images[i] = null;
        	}
        	resizedImage = null;
        }
        
        public void toggleShowNumbers() {
        	showNumbers = !showNumbers;
        }
        
        public void reset() {
        	movingPiece = false;
			time = 0;
			dontDraw = -1;
			amt = 0;
			didMove = false;
			moves = 0;
			didShowWinMessage = false;
			xSpace = initXSpace;
        	ySpace = initYSpace;
        	xOffset = initXOffset;
        	yOffset = initYOffset;
			
			for (int i=0; i<num; i++)
				for (int j=0; j<num; j++)
					positions[i*num + j] = initialPositions[i*num + j];
			
			state = STATE_ACTIVE;
        }
    }
    
    class AppThread extends Thread
    {
    	private SurfaceHolder surfaceHolder;
    	private PuzzleView view;
    	private boolean run;
    	private boolean paused;
    	private Object pauseLock;
    	
    	public AppThread(SurfaceHolder surfaceHolder, PuzzleView view)
    	{
    		this.surfaceHolder = surfaceHolder;
    		this.view = view;
    		pauseLock = new Object();
    	}
    	
    	public void setRunning(boolean running) {
    		run = running;
    	}
    	
    	public boolean isRunning() {
    		return run;
    	}
    	
    	public boolean isPaused() {
    		return paused;
    	}
    	
    	@Override
    	public void run()
    	{
    		Canvas canvas;
    		while (run)
    		{
    			canvas = null;
    			try {
    				canvas = surfaceHolder.lockCanvas(null);
    				synchronized (surfaceHolder) {
    					view.update();
    					view.onDraw(canvas);
    				}
    			} finally {
    				if (canvas!=null)
    					surfaceHolder.unlockCanvasAndPost(canvas);
    			}
    			synchronized (pauseLock) {
    				while (paused) {
    					try {
    						pauseLock.wait();
    					} catch (InterruptedException e) {
    					}
    				}
    			}
    		}
    	}
    	
    	public void pause() {
    		synchronized (pauseLock) {
    			paused = true;
    		}
    	}
    	
    	public void unpause() {
    		synchronized (pauseLock) {
    			paused = false;
    			pauseLock.notifyAll();
    		}
    	}
    }
    //end class Thread
    
    private boolean playing;
    
    private static final int MENU_DIFF = 0, MENU_DIFF_EASY = 1, MENU_DIFF_MEDIUM = 2, MENU_DIFF_HARD = 3, MENU_DIFF_SUPER_EASY = -1,
    		MENU_NEW = 4, MENU_TEXT = 5, MENU_RESET = 6;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        //menu.add(0, MENU_DIFFICULTY, 0, "Difficulty");
        menu.add(0, MENU_NEW, 0, "New");
        menu.add(0, MENU_RESET, 0, "Retry");
        SubMenu sub = menu.addSubMenu(0, MENU_DIFF, 0, "Difficulty");
        sub.add(0, MENU_DIFF_EASY, 0, "Easy");
        sub.add(0, MENU_DIFF_MEDIUM, 0, "Medium");
        sub.add(0, MENU_DIFF_HARD, 0, "Hard");
        sub.add(0, MENU_DIFF_SUPER_EASY, 0, "Super Easy");
        menu.add(0, MENU_TEXT, 0, "Show Numbers");
        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return playing;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
	        case MENU_DIFF_SUPER_EASY:
	        	game.setDifficulty(2);
	            return true;
            case MENU_DIFF_EASY:
            	game.setDifficulty(3);
                return true;
            case MENU_DIFF_MEDIUM:
            	game.setDifficulty(4);
            	return true;
            case MENU_DIFF_HARD:
            	game.setDifficulty(5);
            	return true;
            case MENU_NEW:
            	game.pause();
            	game.freeImages();
            	openMenu();
            	return true;
            case MENU_TEXT:
            	game.toggleShowNumbers();
            	return true;
            case MENU_RESET:
            	game.reset();
            	return true;
        }

        return false;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	if (playing && keyCode == KeyEvent.KEYCODE_BACK) {
	    	new AlertDialog.Builder(this).setMessage(
                "All progress will be lost.").setTitle(
                "Really Quit?").setCancelable(false)
                .setPositiveButton("Yea",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            	game.freeImages();
                                openMenu();
                            }
                        }).setNeutralButton("Nah",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {}
                        }).show();
	    	return true;
    	}
    	return super.onKeyDown(keyCode, event);
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	if (game!=null) game.save();
    }
    
    public void openMenu() {
    	setContentView(R.layout.menu);
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        final ImageButton button1 = (ImageButton) findViewById(R.id.button1), button2 = (ImageButton) findViewById(R.id.button2),
        		button3 = (ImageButton) findViewById(R.id.button3);
        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	getPhoto();
            }
        });
        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	takePhoto();
            }
        });
        button3.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				openGame(BitmapFactory.decodeResource(getResources(), R.drawable.pic));
			}
		});
        playing = false;
    }
    
    public void openGame(Bitmap bitmap) {
    	game.init(bitmap);
    	setContentView(game);
    	playing = true;
    }
    
    //Camera Intent
    private Uri imageUri;
    static final int TAKE_PICTURE = 0, GET_PICTURE = 1;

    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Conpuzzled");
        if (!dir.exists()) dir.mkdirs();
        File photo = new File(dir, "Pic.jpg");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photo));
        imageUri = Uri.fromFile(photo);
        startActivityForResult(intent, TAKE_PICTURE);
    }
    
    public void getPhoto() {
	    Intent intent = new Intent();
	    intent.setAction(Intent.ACTION_PICK);
	    intent.setType("image/*");
	    startActivityForResult(intent, GET_PICTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri selectedImage = null;
        if (resultCode == RESULT_OK)
	        switch (requestCode) {
		        case TAKE_PICTURE:
		            selectedImage = imageUri;
		            break;
		        case GET_PICTURE:
		        	selectedImage = data.getData();
		        	break;
	        }
        
        if (selectedImage!=null) {
        	getContentResolver().notifyChange(selectedImage, null);
        	ContentResolver cr = this.getContentResolver();
        	Bitmap bitmap;
	        try {
	        	bitmap = Images.Media.getBitmap(cr, selectedImage);
	            openGame(bitmap);
	        } catch (Exception e) {
	            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
	            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	            Log.i("Error", e.toString());
	        }
        }
        else {
        	Log.i("Image", "Failed to load");
        }
    }
    
    public void alert(String title, String message) {
    	new AlertDialog.Builder(this).setMessage(
            message).setTitle(
            title).setCancelable(false)
            .setPositiveButton("OK",
            		new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int whichButton) {
                        }
            		}
            ).show();
    }
    
    public String getRealPathFromURI(Uri contentUri) {
    	String [] proj={MediaStore.Images.Media.DATA};
    	Cursor cursor = managedQuery( contentUri,
    			proj, // Which columns to return
    			null,       // WHERE clause; which rows to return (all rows)
    			null,       // WHERE clause selection arguments (none)
    			null); // Order-by clause (ascending by name)
    	int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
    	cursor.moveToFirst();

    	return cursor.getString(column_index);
    }
}