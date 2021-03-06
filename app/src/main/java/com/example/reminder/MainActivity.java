package com.example.reminder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.example.reminder.database.AimDatabase;
import com.example.reminder.database.EachGoal;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements GoalAdapter.ItemClickListener {

    private RecyclerView mRecyclerView;
    private GoalAdapter mGoalAdapter;
    private static AimDatabase mAimDatabase;
    FloatingActionButton mFabAddGoal;
    private FloatingActionButton mFabRefresh;


    private static List<EachGoal> GoalListForShuffling;

    TextView mTvDeadlineTime;
    static TextView mTodayDate;

    DateShuffler dateShuffler;

    public static Context mContext;

    private TreeMap<Integer,Integer> treeMap = new TreeMap<Integer, Integer>();

    private boolean goalDeletedUniversal = false;

    CheckBox mCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        mTvDeadlineTime = findViewById(R.id.tvDeadlineTime);
        mRecyclerView = findViewById(R.id.rvGoals);
        mTodayDate = findViewById(R.id.today_date_tv);
        mCheckBox = findViewById(R.id.checkbox);

        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this);

        mRecyclerView.setLayoutManager(mLinearLayoutManager);

        OnItemClickIndividual.checkBoxClickCallback checkBoxClicked = new OnItemClickIndividual.checkBoxClickCallback() {
            @Override
            public void onItemClicked(View view, int position) {
                switch(view.getId())
                {
                    case R.id.checkbox:
                        if(GoalListForShuffling != null) {
                            if (GoalListForShuffling.size() > 0) {
                                final EachGoal individualAim = GoalListForShuffling.get(position);
                                boolean taskDone = individualAim.isTaskDone();

                                final EachGoal goal = new EachGoal(individualAim.getGoalName(),individualAim.getOriginalDeadlineDate(),
                                        individualAim.getVirtualDeadlineDate(),individualAim.isAllowVD(),individualAim.isVdCreatedForThisAim(),
                                        individualAim.isNearestToTodayDate(),true,individualAim.isNotificationAlarm(),individualAim.isPastDate(),!taskDone);

                                GoalExecutor.getInstance().diskIO().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        goal.setId(individualAim.getId());
                                        mAimDatabase.aimDao().updateGoal(goal);
                                    }

                                });

                            }
                        }
                        break;

                }
            }
        };

        mGoalAdapter = new GoalAdapter(this,this,checkBoxClicked);

        dateShuffler = new DateShuffler();

        mRecyclerView.setAdapter(mGoalAdapter);

//        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(getApplicationContext(),DividerItemDecoration.VERTICAL);
//        mRecyclerView.addItemDecoration(mDividerItemDecoration);

        mFabAddGoal = findViewById(R.id.fabAddGoal);

        mFabAddGoal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentAddGoal = new Intent(MainActivity.this,AddGoal.class);
                startActivity(intentAddGoal);
            }
        });

        mFabRefresh = findViewById(R.id.fabRefresh);
        mFabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nearestToTodaySelector();
            }
        });


        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, int direction) {
                GoalExecutor.getInstance().diskIO().execute(new Runnable() {
                    @Override
                    public void run() {
                        int position = viewHolder.getAdapterPosition();
                        List<EachGoal> allGoals = mGoalAdapter.getGoalList();
                        mAimDatabase.aimDao().deleteGoal(allGoals.get(position));
                    }
                });
            }
        }).attachToRecyclerView(mRecyclerView);
        mAimDatabase = AimDatabase.getInstance(getApplicationContext());
        goalDeletedUniversal = true;
        setUpViewModel(true);

        AimFirebaseJobDispatcher.scheduleJob(mContext);



    }

    public void setUpViewModel(final boolean goalDeleted)
    {
        ViewModelForMainActivity viewModel = ViewModelProviders.of(this).get(ViewModelForMainActivity.class);
        viewModel.getGoalList().observe(this, new Observer<List<EachGoal>>() {
            @Override
            public void onChanged(List<EachGoal> eachGoals) {
                mGoalAdapter.setGoalList(eachGoals);
                dateShuffler.setGoalList(eachGoals);
                GoalListForShuffling = eachGoals;
               // nearestToTodaySelector();
                if(goalDeleted)
                {
                    if(goalDeletedUniversal)
                    {
                        nearestToTodaySelector();
                        goalDeletedUniversal = false;
                    }

                }

            }
        });
    }

    public void shuffleDates()
    {
        dateShuffler.shuffleDates(getApplicationContext());
    }

    public static void setCurrentDate()
    {
        Date date = new Date(System.currentTimeMillis());
        String deadlineDateString = DateFormat.getDateInstance(DateFormat.FULL).format(date);
        mTodayDate.setText(deadlineDateString);
    }
    public static void makeNotification(Context mContext)
    {
        if(GoalListForShuffling != null)
        {
            if(GoalListForShuffling.size()>0)
            {
                for(int i = 0; i < GoalListForShuffling.size(); i++)
                {

                    final EachGoal individualAim = (EachGoal) GoalListForShuffling.get(i);

                    Calendar calendarForEachAim = Calendar.getInstance();
                    calendarForEachAim.setTime(individualAim.getVirtualDeadlineDate());

                    Log.d("avengers", "isNotificationShown for Goal: " + individualAim.getGoalName() + " = " + individualAim.isNotificationShown());
                    Log.d("avengers", "isAlarmShown for Goal: " + individualAim.getGoalName() + " = " + individualAim.isNotificationAlarm());

                    Calendar todayCalendarDefault = Calendar.getInstance();

                    int differenceHours = (int) TimeUnit.HOURS.convert(calendarForEachAim.getTimeInMillis()-todayCalendarDefault.getTimeInMillis(),TimeUnit.MILLISECONDS);

                    // int differenceSeconds = (int) TimeUnit.SECONDS.convert(calendarForEachAim.getTimeInMillis()-todayCalendarDefault.getTimeInMillis(),TimeUnit.MILLISECONDS);

                    boolean notificationShown = false;
                    boolean notificationAlarm = false;

                    if(differenceHours >= 18 && differenceHours <= 24)
                    {
                        if(!individualAim.isNotificationShown())
                        {

                            Log.d("avengers", "showing notification for " + individualAim.getGoalName());

                            Notification.notificationCreator(mContext,individualAim,"Less than " + differenceHours + " hours left for");

                            final EachGoal notificationGoal = new EachGoal(individualAim.getGoalName(),individualAim.getOriginalDeadlineDate(),
                                    individualAim.getVirtualDeadlineDate(),individualAim.isAllowVD(),individualAim.isVdCreatedForThisAim(),
                                    individualAim.isNearestToTodayDate(),true,individualAim.isNotificationAlarm(),individualAim.isPastDate(),individualAim.isTaskDone());

                            GoalExecutor.getInstance().diskIO().execute(new Runnable() {
                                @Override
                                public void run() {
                                    notificationGoal.setId(individualAim.getId());
                                    mAimDatabase.aimDao().updateGoal(notificationGoal);
                                }

                            });


                        }
                    }

                    else if(differenceHours >= 0 && differenceHours <= 2)
                    {
                        if(!individualAim.isNotificationAlarm())
                        {
                            Log.d("avengers", "showing alarm for " + individualAim.getGoalName());

                            Notification.notificationCreator(mContext,individualAim, "Less than " + differenceHours + " hours left for");

                            final EachGoal notificationGoal = new EachGoal(individualAim.getGoalName(),individualAim.getOriginalDeadlineDate(),
                                    individualAim.getVirtualDeadlineDate(),individualAim.isAllowVD(),individualAim.isVdCreatedForThisAim(),
                                    individualAim.isNearestToTodayDate(),individualAim.isNotificationShown(),true,individualAim.isPastDate(),individualAim.isTaskDone());

                            GoalExecutor.getInstance().diskIO().execute(new Runnable() {
                                @Override
                                public void run() {
                                    notificationGoal.setId(individualAim.getId());
                                    mAimDatabase.aimDao().updateGoal(notificationGoal);
                                }
                            });

                        }
                    }

                    Log.d("avengers","\n");
                }
            }
        }
    }

    public void nearestToTodaySelector()
    {
        if(GoalListForShuffling != null) {
            if (GoalListForShuffling.size() > 0) {
                for (int i = 0; i < GoalListForShuffling.size(); i++) {

                    final EachGoal individualAim = (EachGoal) GoalListForShuffling.get(i);


                    Calendar calendarForEachAim = Calendar.getInstance();
                    calendarForEachAim.setTime(individualAim.getVirtualDeadlineDate());

                    Log.d("peterparker", individualAim.getGoalName() + " " + String.valueOf(individualAim.isVdCreatedForThisAim()));
                    Calendar todayCalendarDefault = Calendar.getInstance();


                    int differenceHours = (int) TimeUnit.HOURS.convert(calendarForEachAim.getTimeInMillis()-todayCalendarDefault.getTimeInMillis(),TimeUnit.MILLISECONDS);

                    boolean nearestToToday = false;
                    boolean pastDate = false;

                    if(differenceHours < 0)
                    {
                        nearestToToday = false;
                        pastDate = true;
                    }
                    else if(differenceHours >=0 && differenceHours <= 24)
                    {
                        nearestToToday = true;
                        pastDate = false;
                    }
                    else if(differenceHours > 24)
                    {
                        nearestToToday = false;
                        pastDate = false;
                    }

                    final EachGoal alarmGoal = new EachGoal(individualAim.getGoalName(),individualAim.getOriginalDeadlineDate(),
                            individualAim.getVirtualDeadlineDate(),individualAim.isAllowVD(),individualAim.isVdCreatedForThisAim(),
                            nearestToToday,individualAim.isNotificationShown(),individualAim.isNotificationAlarm(),pastDate,individualAim.isTaskDone());

                    GoalExecutor.getInstance().diskIO().execute(new Runnable() {
                        @Override
                        public void run() {
                            alarmGoal.setId(individualAim.getId());
                            mAimDatabase.aimDao().updateGoal(alarmGoal);
                        }
                    });

                }
                mGoalAdapter.setGoalList(GoalListForShuffling);
            }
        }
    }


    @Override
    public void onItemClickListener(int itemId)
    {
        Intent intentGoalClicked = new Intent(MainActivity.this,AddGoal.class);
        intentGoalClicked.putExtra(AddGoal.GOAL_ID_EXTRA,itemId);
        startActivity(intentGoalClicked);
    }
}