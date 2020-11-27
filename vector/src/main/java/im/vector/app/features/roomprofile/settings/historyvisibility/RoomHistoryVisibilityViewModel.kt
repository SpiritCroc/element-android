/*
 * Copyright (c) 2020 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.roomprofile.settings.historyvisibility

import com.airbnb.mvrx.FragmentViewModelContext
import com.airbnb.mvrx.MvRxViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import im.vector.app.core.platform.EmptyAction
import im.vector.app.core.platform.EmptyViewEvents
import im.vector.app.core.platform.VectorViewModel

class RoomHistoryVisibilityViewModel @AssistedInject constructor(@Assisted initialState: RoomHistoryVisibilityState)
    : VectorViewModel<RoomHistoryVisibilityState, EmptyAction, EmptyViewEvents>(initialState) {

    @AssistedInject.Factory
    interface Factory {
        fun create(initialState: RoomHistoryVisibilityState): RoomHistoryVisibilityViewModel
    }

    companion object : MvRxViewModelFactory<RoomHistoryVisibilityViewModel, RoomHistoryVisibilityState> {
        @JvmStatic
        override fun create(viewModelContext: ViewModelContext, state: RoomHistoryVisibilityState): RoomHistoryVisibilityViewModel? {
            val fragment: RoomHistoryVisibilityBottomSheet = (viewModelContext as FragmentViewModelContext).fragment()
            return fragment.roomHistoryVisibilityViewModelFactory.create(state)
        }
    }

    override fun handle(action: EmptyAction) {
        // No op
    }
}
