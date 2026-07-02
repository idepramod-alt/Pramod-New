package com.google.android.material.sidesheet;

import android.view.View;
import android.view.ViewGroup;
import androidx.customview.widget.ViewDragHelper;
/* loaded from: classes.dex */
final class RightSheetDelegate extends SheetDelegate {
    final SideSheetBehavior<? extends View> sheetBehavior;

    /* JADX INFO: Access modifiers changed from: package-private */
    public RightSheetDelegate(SideSheetBehavior<? extends View> sheetBehavior) {
        this.sheetBehavior = sheetBehavior;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public int getSheetEdge() {
        return 0;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public int getHiddenOffset() {
        return this.sheetBehavior.getParentWidth();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public int getExpandedOffset() {
        return Math.max(0, (getHiddenOffset() - this.sheetBehavior.getChildWidth()) - this.sheetBehavior.getInnerMargin());
    }

    private boolean isReleasedCloseToOriginEdge(View releasedChild) {
        return releasedChild.getLeft() > (getHiddenOffset() - getExpandedOffset()) / 2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public int calculateTargetStateOnViewReleased(View releasedChild, float xVelocity, float yVelocity) {
        if (xVelocity < 0.0f) {
            return 3;
        }
        if (shouldHide(releasedChild, xVelocity)) {
            if (isSwipeSignificant(xVelocity, yVelocity) || isReleasedCloseToOriginEdge(releasedChild)) {
                return 5;
            }
            return 3;
        }
        int targetState = (xVelocity > 0.0f ? 1 : (xVelocity == 0.0f ? 0 : -1));
        if (targetState == 0 || !SheetUtils.isSwipeMostlyHorizontal(xVelocity, yVelocity)) {
            int currentLeft = releasedChild.getLeft();
            if (Math.abs(currentLeft - getExpandedOffset()) < Math.abs(currentLeft - getHiddenOffset())) {
                return 3;
            }
            return 5;
        }
        return 5;
    }

    private boolean isSwipeSignificant(float xVelocity, float yVelocity) {
        return SheetUtils.isSwipeMostlyHorizontal(xVelocity, yVelocity) && yVelocity > ((float) this.sheetBehavior.getSignificantVelocityThreshold());
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public boolean shouldHide(View child, float velocity) {
        float newRight = child.getRight() + (this.sheetBehavior.getHideFriction() * velocity);
        return Math.abs(newRight) > this.sheetBehavior.getHideThreshold();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public boolean isSettling(View child, int state, boolean isReleasingView) {
        int left = this.sheetBehavior.getOuterEdgeOffsetForState(state);
        ViewDragHelper viewDragHelper = this.sheetBehavior.getViewDragHelper();
        return viewDragHelper != null && (!isReleasingView ? !viewDragHelper.smoothSlideViewTo(child, left, child.getTop()) : !viewDragHelper.settleCapturedViewAt(left, child.getTop()));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public <V extends View> int getOuterEdge(V child) {
        return child.getLeft() - this.sheetBehavior.getInnerMargin();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public float calculateSlideOffset(int left) {
        float hiddenOffset = getHiddenOffset();
        float sheetWidth = hiddenOffset - getExpandedOffset();
        return (hiddenOffset - left) / sheetWidth;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public void updateCoplanarSiblingLayoutParams(ViewGroup.MarginLayoutParams coplanarSiblingLayoutParams, int sheetLeft, int sheetRight) {
        int parentWidth = this.sheetBehavior.getParentWidth();
        if (sheetLeft <= parentWidth) {
            coplanarSiblingLayoutParams.rightMargin = parentWidth - sheetLeft;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    @Override // com.google.android.material.sidesheet.SheetDelegate
    public int calculateInnerMargin(ViewGroup.MarginLayoutParams marginLayoutParams) {
        return marginLayoutParams.rightMargin;
    }
}
