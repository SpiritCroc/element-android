/*
 * Copyright 2020 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.roomprofile.alias

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.airbnb.mvrx.args
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import im.vector.app.R
import im.vector.app.core.dialogs.withColoredButton
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.extensions.exhaustive
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.utils.shareText
import im.vector.app.core.utils.toast
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.roomprofile.RoomProfileArgs
import im.vector.app.features.roomprofile.alias.detail.RoomAliasBottomSheet
import im.vector.app.features.roomprofile.alias.detail.RoomAliasBottomSheetSharedAction
import im.vector.app.features.roomprofile.alias.detail.RoomAliasBottomSheetSharedActionViewModel
import kotlinx.android.synthetic.main.fragment_room_setting_generic.*
import kotlinx.android.synthetic.main.merge_overlay_waiting_view.*
import org.matrix.android.sdk.api.session.room.alias.RoomAliasError
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

class RoomAliasFragment @Inject constructor(
        val viewModelFactory: RoomAliasViewModel.Factory,
        private val controller: RoomAliasController,
        private val avatarRenderer: AvatarRenderer
) :
        VectorBaseFragment(),
        RoomAliasController.Callback {

    private val viewModel: RoomAliasViewModel by fragmentViewModel()
    private lateinit var sharedActionViewModel: RoomAliasBottomSheetSharedActionViewModel

    private val roomProfileArgs: RoomProfileArgs by args()

    override fun getLayoutResId() = R.layout.fragment_room_setting_generic

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedActionViewModel = activityViewModelProvider.get(RoomAliasBottomSheetSharedActionViewModel::class.java)

        controller.callback = this
        setupToolbar(roomSettingsToolbar)
        roomSettingsRecyclerView.configureWith(controller, hasFixedSize = true)
        waiting_view_status_text.setText(R.string.please_wait)
        waiting_view_status_text.isVisible = true

        viewModel.observeViewEvents {
            when (it) {
                is RoomAliasViewEvents.Failure -> showFailure(it.throwable)
                RoomAliasViewEvents.Success    -> showSuccess()
            }.exhaustive
        }

        sharedActionViewModel
                .observe()
                .subscribe { handleAliasAction(it) }
                .disposeOnDestroyView()
    }

    private fun handleAliasAction(action: RoomAliasBottomSheetSharedAction?) {
        when (action) {
            is RoomAliasBottomSheetSharedAction.ShareAlias     -> shareAlias(action.matrixTo)
            is RoomAliasBottomSheetSharedAction.PublishAlias   -> viewModel.handle(RoomAliasAction.PublishAlias(action.alias))
            is RoomAliasBottomSheetSharedAction.UnPublishAlias -> unpublishAlias(action.alias)
            is RoomAliasBottomSheetSharedAction.DeleteAlias    -> removeLocalAlias(action.alias)
            is RoomAliasBottomSheetSharedAction.SetMainAlias   -> viewModel.handle(RoomAliasAction.SetCanonicalAlias(action.alias))
            RoomAliasBottomSheetSharedAction.UnsetMainAlias    -> viewModel.handle(RoomAliasAction.SetCanonicalAlias(canonicalAlias = null))
            null                                               -> Unit
        }
    }

    private fun shareAlias(matrixTo: String) {
        shareText(requireContext(), matrixTo)
    }

    override fun showFailure(throwable: Throwable) {
        if (throwable !is RoomAliasError) {
            super.showFailure(throwable)
        }
    }

    private fun showSuccess() {
        activity?.toast(R.string.room_settings_save_success)
    }

    override fun onDestroyView() {
        controller.callback = null
        roomSettingsRecyclerView.cleanup()
        super.onDestroyView()
    }

    override fun invalidate() = withState(viewModel) { viewState ->
        controller.setData(viewState)
        renderRoomSummary(viewState)
    }

    private fun renderRoomSummary(state: RoomAliasViewState) {
        waiting_view.isVisible = state.isLoading

        state.roomSummary()?.let {
            roomSettingsToolbarTitleView.text = it.displayName
            avatarRenderer.render(it.toMatrixItem(), roomSettingsToolbarAvatarImageView)
        }

        invalidateOptionsMenu()
    }

    private fun unpublishAlias(altAlias: String) {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_confirmation)
                .setMessage(getString(R.string.room_alias_delete_confirmation, altAlias))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.handle(RoomAliasAction.UnpublishAlias(altAlias))
                }
                .show()
                .withColoredButton(DialogInterface.BUTTON_POSITIVE)
    }

    override fun toggleManualPublishForm() {
        viewModel.handle(RoomAliasAction.ToggleManualPublishForm)
    }

    override fun setNewAlias(value: String) {
        viewModel.handle(RoomAliasAction.SetNewAlias(value))
    }

    override fun addAlias() {
        viewModel.handle(RoomAliasAction.ManualPublishAlias)
    }

    override fun toggleLocalAliasForm() {
        viewModel.handle(RoomAliasAction.ToggleAddLocalAliasForm)
    }

    override fun setNewLocalAliasLocalPart(value: String) {
        viewModel.handle(RoomAliasAction.SetNewLocalAliasLocalPart(value))
    }

    override fun addLocalAlias() {
        viewModel.handle(RoomAliasAction.AddLocalAlias)
    }

    override fun openAlias(alias: String, isPublished: Boolean) = withState(viewModel) { state ->
        RoomAliasBottomSheet
                .newInstance(
                        alias = alias,
                        isPublished = isPublished,
                        isMainAlias = alias == state.canonicalAlias,
                        canEditCanonicalAlias = state.actionPermissions.canChangeCanonicalAlias
                )
                .show(childFragmentManager, "ROOM_ALIAS_ACTIONS")
    }

    private fun removeLocalAlias(alias: String) {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_confirmation)
                .setMessage(getString(R.string.room_alias_delete_confirmation, alias))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.handle(RoomAliasAction.RemoveLocalAlias(alias))
                }
                .show()
                .withColoredButton(DialogInterface.BUTTON_POSITIVE)
    }
}
