/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hs.administrator.test.adapter.animator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;

import java.util.ArrayList;
import java.util.List;


public class RItemAnimator extends SimpleItemAnimator {
    private static final String TAG = "DefaultItemAnimator";
    private static final boolean DEBUG = false;

    private static TimeInterpolator sDefaultInterpolator;

    private ArrayList<ViewHolder> mPendingRemovals = new ArrayList<>();
    private ArrayList<ViewHolder> mPendingAdditions = new ArrayList<>();
    private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

    ArrayList<ArrayList<ViewHolder>> mAdditionsList = new ArrayList<>();
    ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
    ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();

    ArrayList<ViewHolder> mAddAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mMoveAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mRemoveAnimations = new ArrayList<>();
    ArrayList<ViewHolder> mChangeAnimations = new ArrayList<>();

    private static class MoveInfo {
        public ViewHolder holder;
        public int fromX, fromY, toX, toY;

        MoveInfo(ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    private static class ChangeInfo {
        public ViewHolder oldHolder, newHolder;
        public int fromX, fromY, toX, toY;

        private ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder) {
            this.oldHolder = oldHolder;
            this.newHolder = newHolder;
        }

        ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder,
                   int fromX, int fromY, int toX, int toY) {
            this(oldHolder, newHolder);
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }

        @Override
        public String toString() {
            return "ChangeInfo{"
                    + "oldHolder=" + oldHolder
                    + ", newHolder=" + newHolder
                    + ", fromX=" + fromX
                    + ", fromY=" + fromY
                    + ", toX=" + toX
                    + ", toY=" + toY
                    + '}';
        }
    }

    @Override
    public void runPendingAnimations() {
        Log.e(TAG, "runPendingAnimations: ");
        boolean removalsPending = !mPendingRemovals.isEmpty();
        boolean movesPending = !mPendingMoves.isEmpty();
        boolean changesPending = !mPendingChanges.isEmpty();
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            // nothing to animate
            return;
        }
        // First, remove stuff
        for (ViewHolder holder : mPendingRemovals) {
            animateRemoveImpl(holder);
        }
        mPendingRemovals.clear();
        // Next, move stuff
        if (movesPending) {
            final ArrayList<MoveInfo> moves = new ArrayList<>();
            moves.addAll(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();
            Runnable mover = new Runnable() {
                @Override
                public void run() {
                    for (MoveInfo moveInfo : moves) {
                        animateMoveImpl(moveInfo.holder, moveInfo.fromX, moveInfo.fromY,
                                moveInfo.toX, moveInfo.toY);
                    }
                    moves.clear();
                    mMovesList.remove(moves);
                }
            };
            if (removalsPending) {
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, getRemoveDuration());
            } else {
                mover.run();
            }
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {
            final ArrayList<ChangeInfo> changes = new ArrayList<>();
            changes.addAll(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();
            Runnable changer = () -> {
                for (ChangeInfo change : changes) {
                    animateChangeImpl(change);
                }
                changes.clear();
                mChangesList.remove(changes);
            };
            if (removalsPending) {
                ViewHolder holder = changes.get(0).oldHolder;
                ViewCompat.postOnAnimationDelayed(holder.itemView, changer, getRemoveDuration());
            } else {
                changer.run();
            }
        }
        // Next, add stuff
        if (additionsPending) {
            final ArrayList<ViewHolder> additions = new ArrayList<>();
            additions.addAll(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();
            Runnable adder = () -> {
                for (ViewHolder holder : additions) {
                    animateAddImpl(holder);
                }
                additions.clear();
                mAdditionsList.remove(additions);
            };
            if (removalsPending || movesPending || changesPending) {
                long removeDuration = removalsPending ? getRemoveDuration() : 0;
                long moveDuration = movesPending ? getMoveDuration() : 0;
                long changeDuration = changesPending ? getChangeDuration() : 0;
                long totalDelay = removeDuration + Math.max(moveDuration, changeDuration);
                View view = additions.get(0).itemView;
                ViewCompat.postOnAnimationDelayed(view, adder, totalDelay);
            } else {
                adder.run();
            }
        }
    }

    @Override
    public boolean animateRemove(final ViewHolder holder) {
        Log.e(TAG, "animateRemove: ");
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        return true;
    }

    private void animateRemoveImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mRemoveAnimations.add(holder);
        animation.setDuration(getRemoveDuration()).alpha(0).setListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchRemoveStarting(holder);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        view.setAlpha(1);
                        dispatchRemoveFinished(holder);
                        mRemoveAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                }).start();
    }

    @Override
    public boolean animateAdd(final ViewHolder holder) {
        resetAnimation(holder);//重置动画
        holder.itemView.setAlpha(0);//将该条目透明度设为0,也就是点击时的空白区域
        mPendingAdditions.add(holder);//将这个透明的条目加入mPendingAdditions列表
        return true;
    }

    void animateAddImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        mAddAnimations.add(holder);

        //移动
        ObjectAnimator translationX = ObjectAnimator//创建实例
                //(View,属性名,初始化值,结束值)
                .ofFloat(view, "translationX", 0, 20, -20, 0, 20, -20, 0, 20, -20, 0)
                .setDuration(300);//设置时长

        //缩放
        ObjectAnimator scaleX = ObjectAnimator//创建实例
                //(View,属性名,初始化值,结束值)
                .ofFloat(view, "scaleX", 1, 0.95f, 1.05f, 1, 0.95f, 1.05f, 1, 0.95f, 1.05f, 1)
                .setDuration(300);//设置时长

        //定轴旋转
        ObjectAnimator rotationY = ObjectAnimator//创建实例
                //(View,属性名,初始化值,结束值)
                .ofFloat(view, "rotationY", 0, 360)
                .setDuration(1000);//设置时长

        ObjectAnimator rotationX = ObjectAnimator//创建实例
                //(View,属性名,初始化值,结束值)
                .ofFloat(view, "rotationX", 0, 360)
                .setDuration(1000);//设置时长

        AnimatorSet set = new AnimatorSet();
//        set.playTogether(scaleX, translationX);//两个效果一起
//        set.playSequentially(translationX);//添加动画
//        set.playSequentially(scaleX);//添加动画
//        set.playSequentially(scaleY);//添加动画
//        set.playSequentially(rotationX);//添加动画
        set.playSequentially(rotationY);//添加动画

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                view.setAlpha(1);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                dispatchAddFinished(holder);
                mAddAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                view.setAlpha(1);
                dispatchAddStarting(holder);
            }
        });

        set.start();


    }

    @Override
    public boolean animateMove(final ViewHolder holder, int fromX, int fromY,
                               int toX, int toY) {
        final View view = holder.itemView;
        fromX += (int) holder.itemView.getTranslationX();
        fromY += (int) holder.itemView.getTranslationY();
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        if (deltaX != 0) {
            view.setTranslationX(-deltaX);
        }
        if (deltaY != 0) {
            view.setTranslationY(-deltaY);
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    void animateMoveImpl(final ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            view.animate().translationX(0);
        }
        if (deltaY != 0) {
            view.animate().translationY(0);
        }
        // TODO: make EndActions end listeners instead, since end actions aren't called when
        // vpas are canceled (and can't end them. why?)
        // need listener functionality in VPACompat for this. Ick.
        final ViewPropertyAnimator animation = view.animate();
        mMoveAnimations.add(holder);

        //定轴旋转
//        ObjectAnimator//创建实例
//                .ofFloat(view, "rotationY", 0, 360)
//                .setDuration(1000).start();//设置时长

//
        ObjectAnimator//创建实例
                .ofFloat(view, "ScaleX", 1, 0.5f, 1.2f,0.8f,1)
                .setDuration(1000).start();//设置时长
        ObjectAnimator//创建实例
                .ofFloat(view, "ScaleY", 1, 0.5f, 1.2f,0.8f,1)
                .setDuration(1000).start();//设置时长

        animation.setDuration(getMoveDuration()).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                dispatchMoveStarting(holder);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                if (deltaX != 0) {
                    view.setTranslationX(0);
                }
                if (deltaY != 0) {
                    view.setTranslationY(0);
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animation.setListener(null);
                dispatchMoveFinished(holder);
                mMoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    @Override
    public boolean animateChange(ViewHolder oldHolder, ViewHolder newHolder,
                                 int fromX, int fromY, int toX, int toY) {
        Log.e(TAG, "animateChange: ");
        if (oldHolder == newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder, fromX, fromY, toX, toY);
        }
        final float prevTranslationX = oldHolder.itemView.getTranslationX();
        final float prevTranslationY = oldHolder.itemView.getTranslationY();
        final float prevAlpha = oldHolder.itemView.getAlpha();
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        // recover prev translation state after ending animation
        oldHolder.itemView.setTranslationX(prevTranslationX);
        oldHolder.itemView.setTranslationY(prevTranslationY);
        oldHolder.itemView.setAlpha(prevAlpha);
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder);
            newHolder.itemView.setTranslationX(-deltaX);
            newHolder.itemView.setTranslationY(-deltaY);
            newHolder.itemView.setAlpha(0);
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        return true;
    }

    void animateChangeImpl(final ChangeInfo changeInfo) {
        final ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        if (view != null) {
            final ViewPropertyAnimator oldViewAnim = view.animate().setDuration(
                    getChangeDuration());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);

            ObjectAnimator//创建实例
                    .ofFloat(view, "ScaleX", 1, 0.5f, 1.2f,0.8f,1)
                    .setDuration(1000).start();//设置时长
            ObjectAnimator//创建实例
                    .ofFloat(view, "ScaleY", 1, 0.5f, 1.2f,0.8f,1)
                    .setDuration(1000).start();//设置时长

            oldViewAnim.alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.oldHolder, true);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    oldViewAnim.setListener(null);
                    view.setAlpha(1);
                    view.setTranslationX(0);
                    view.setTranslationY(0);
                    dispatchChangeFinished(changeInfo.oldHolder, true);
                    mChangeAnimations.remove(changeInfo.oldHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
        if (newView != null) {
            final ViewPropertyAnimator newViewAnimation = newView.animate();
            mChangeAnimations.add(changeInfo.newHolder);
            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration())
                    .alpha(1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchChangeStarting(changeInfo.newHolder, false);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    newViewAnimation.setListener(null);
                    newView.setAlpha(1);
                    newView.setTranslationX(0);
                    newView.setTranslationY(0);
                    dispatchChangeFinished(changeInfo.newHolder, false);
                    mChangeAnimations.remove(changeInfo.newHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
    }

    private void endChangeAnimation(List<ChangeInfo> infoList, ViewHolder item) {
        for (int i = infoList.size() - 1; i >= 0; i--) {
            ChangeInfo changeInfo = infoList.get(i);
            if (endChangeAnimationIfNecessary(changeInfo, item)) {
                if (changeInfo.oldHolder == null && changeInfo.newHolder == null) {
                    infoList.remove(changeInfo);
                }
            }
        }
    }

    private void endChangeAnimationIfNecessary(ChangeInfo changeInfo) {
        if (changeInfo.oldHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder);
        }
        if (changeInfo.newHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder);
        }
    }

    private boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, ViewHolder item) {
        boolean oldItem = false;
        if (changeInfo.newHolder == item) {
            changeInfo.newHolder = null;
        } else if (changeInfo.oldHolder == item) {
            changeInfo.oldHolder = null;
            oldItem = true;
        } else {
            return false;
        }
        item.itemView.setAlpha(1);
        item.itemView.setTranslationX(0);
        item.itemView.setTranslationY(0);
        dispatchChangeFinished(item, oldItem);
        return true;
    }

    @Override
    public void endAnimation(ViewHolder item) {
        Log.e(TAG, "endAnimation: ");
        final View view = item.itemView;//条目视图
        view.animate().cancel();//先取消条目视图的动画

        for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = mPendingMoves.get(i);
            if (moveInfo.holder == item) {
                view.setTranslationY(0);
                view.setTranslationX(0);
                dispatchMoveFinished(item);
                mPendingMoves.remove(i);
            }
        }

        endChangeAnimation(mPendingChanges, item);
        if (mPendingRemovals.remove(item)) {
            view.setAlpha(1);
            dispatchRemoveFinished(item);
        }

        //添加的条目布局列表：mPendingAdditions
        if (mPendingAdditions.remove(item)) {//移除该条目
            view.setAlpha(1);//将该条目透明度设为1
            dispatchAddFinished(item);
        }

        for (int i = mChangesList.size() - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            endChangeAnimation(changes, item);
            if (changes.isEmpty()) {
                mChangesList.remove(i);
            }
        }
        for (int i = mMovesList.size() - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            for (int j = moves.size() - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                if (moveInfo.holder == item) {
                    view.setTranslationY(0);
                    view.setTranslationX(0);
                    dispatchMoveFinished(item);
                    moves.remove(j);
                    if (moves.isEmpty()) {
                        mMovesList.remove(i);
                    }
                    break;
                }
            }
        }
        for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            if (additions.remove(item)) {
                view.setAlpha(1);
                dispatchAddFinished(item);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(i);
                }
            }
        }

        // animations should be ended by the cancel above.
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mRemoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mRemoveAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mAddAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mAddAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mChangeAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mChangeAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mMoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mMoveAnimations list");
        }
        dispatchFinishedWhenDone();
    }

    private void resetAnimation(ViewHolder holder) {
        if (sDefaultInterpolator == null) {
            sDefaultInterpolator = new ValueAnimator().getInterpolator();
        }
        holder.itemView.animate().setInterpolator(sDefaultInterpolator);
        endAnimation(holder);
    }

    @Override
    public boolean isRunning() {

        return (!mPendingAdditions.isEmpty()
                || !mPendingChanges.isEmpty()
                || !mPendingMoves.isEmpty()
                || !mPendingRemovals.isEmpty()
                || !mMoveAnimations.isEmpty()
                || !mRemoveAnimations.isEmpty()
                || !mAddAnimations.isEmpty()
                || !mChangeAnimations.isEmpty()
                || !mMovesList.isEmpty()
                || !mAdditionsList.isEmpty()
                || !mChangesList.isEmpty());
    }

    /**
     * Check the state of currently pending and running animations. If there are none
     * pending/running, call {@link #dispatchAnimationsFinished()} to notify any
     * listeners.
     */
    void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }

    @Override
    public void endAnimations() {
        Log.e(TAG, "endAnimations: ");
        int count = mPendingMoves.size();
        for (int i = count - 1; i >= 0; i--) {
            MoveInfo item = mPendingMoves.get(i);
            View view = item.holder.itemView;
            view.setTranslationY(0);
            view.setTranslationX(0);
            dispatchMoveFinished(item.holder);
            mPendingMoves.remove(i);
        }
        count = mPendingRemovals.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder item = mPendingRemovals.get(i);
            dispatchRemoveFinished(item);
            mPendingRemovals.remove(i);
        }
        count = mPendingAdditions.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder item = mPendingAdditions.get(i);
            item.itemView.setAlpha(1);
            dispatchAddFinished(item);
            mPendingAdditions.remove(i);
        }
        count = mPendingChanges.size();
        for (int i = count - 1; i >= 0; i--) {
            endChangeAnimationIfNecessary(mPendingChanges.get(i));
        }
        mPendingChanges.clear();
        if (!isRunning()) {
            return;
        }

        int listCount = mMovesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            count = moves.size();
            for (int j = count - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                ViewHolder item = moveInfo.holder;
                View view = item.itemView;
                view.setTranslationY(0);
                view.setTranslationX(0);
                dispatchMoveFinished(moveInfo.holder);
                moves.remove(j);
                if (moves.isEmpty()) {
                    mMovesList.remove(moves);
                }
            }
        }
        listCount = mAdditionsList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            count = additions.size();
            for (int j = count - 1; j >= 0; j--) {
                ViewHolder item = additions.get(j);
                View view = item.itemView;
                view.setAlpha(1);
                dispatchAddFinished(item);
                additions.remove(j);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(additions);
                }
            }
        }
        listCount = mChangesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            count = changes.size();
            for (int j = count - 1; j >= 0; j--) {
                endChangeAnimationIfNecessary(changes.get(j));
                if (changes.isEmpty()) {
                    mChangesList.remove(changes);
                }
            }
        }

        cancelAll(mRemoveAnimations);
        cancelAll(mMoveAnimations);
        cancelAll(mAddAnimations);
        cancelAll(mChangeAnimations);

        dispatchAnimationsFinished();
    }

    void cancelAll(List<ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            viewHolders.get(i).itemView.animate().cancel();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the payload list is not empty, DefaultItemAnimator returns <code>true</code>.
     * When this is the case:
     * <ul>
     * <li>If you override {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)}, both
     * ViewHolder arguments will be the same instance.
     * </li>
     * <li>
     * If you are not overriding {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)},
     * then DefaultItemAnimator will call {@link #animateMove(ViewHolder, int, int, int, int)} and
     * run a move animation instead.
     * </li>
     * </ul>
     */
    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder,
                                             @NonNull List<Object> payloads) {
        Log.e(TAG, "canReuseUpdatedViewHolder: ");
        return !payloads.isEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads);
    }
}
