/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialershared.bubble;

import android.content.Context;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Checkable;

/**
 * An {@link android.widget.ImageButton ImageButton} that implements {@link Checkable} and
 * propagates the checkable state
 */
public class CheckableImageButton extends AppCompatImageButton implements Checkable {

  // Copied without modification from AppCompat library

  private static final int[] DRAWABLE_STATE_CHECKED = new int[] {android.R.attr.state_checked};

  private boolean mChecked;

  public CheckableImageButton(Context context) {
    this(context, null);
  }

  public CheckableImageButton(Context context, AttributeSet attrs) {
    this(context, attrs, android.R.attr.imageButtonStyle);
  }

  public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    ViewCompat.setAccessibilityDelegate(
        this,
        new AccessibilityDelegateCompat() {
          @Override
          public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setChecked(isChecked());
          }

          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setCheckable(true);
            info.setChecked(isChecked());
          }
        });
  }

  @Override
  public void setChecked(boolean checked) {
    if (mChecked != checked) {
      mChecked = checked;
      refreshDrawableState();
      sendAccessibilityEvent(AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
    }
  }

  @Override
  public boolean isChecked() {
    return mChecked;
  }

  @Override
  public void toggle() {
    setChecked(!mChecked);
  }

  @Override
  public int[] onCreateDrawableState(int extraSpace) {
    if (mChecked) {
      return mergeDrawableStates(
          super.onCreateDrawableState(extraSpace + DRAWABLE_STATE_CHECKED.length),
          DRAWABLE_STATE_CHECKED);
    } else {
      return super.onCreateDrawableState(extraSpace);
    }
  }
}
