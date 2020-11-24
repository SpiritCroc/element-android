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

import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import im.vector.app.R
import im.vector.app.core.epoxy.errorWithRetryItem
import im.vector.app.core.epoxy.loadingItem
import im.vector.app.core.epoxy.profiles.buildProfileSection
import im.vector.app.core.epoxy.profiles.profileActionItem
import im.vector.app.core.error.ErrorFormatter
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.resources.StringProvider
import im.vector.app.features.discovery.settingsButtonItem
import im.vector.app.features.discovery.settingsContinueCancelItem
import im.vector.app.features.discovery.settingsInfoItem
import im.vector.app.features.form.formEditTextItem
import im.vector.app.features.roomdirectory.createroom.RoomAliasErrorFormatter
import im.vector.app.features.roomdirectory.createroom.roomAliasEditItem
import org.matrix.android.sdk.api.session.room.alias.RoomAliasError
import javax.inject.Inject

class RoomAliasController @Inject constructor(
        private val stringProvider: StringProvider,
        private val errorFormatter: ErrorFormatter,
        private val colorProvider: ColorProvider,
        private val roomAliasErrorFormatter: RoomAliasErrorFormatter
) : TypedEpoxyController<RoomAliasViewState>() {

    interface Callback {
        fun toggleManualPublishForm()
        fun setNewAlias(value: String)
        fun addAlias()
        // TODO Delete some methods below
        fun removeAlias(altAlias: String)
        fun setCanonicalAlias(alias: String?)
        fun removeLocalAlias(alias: String)
        fun toggleLocalAliasForm()
        fun setNewLocalAliasLocalPart(value: String)
        fun addLocalAlias()
        fun openAlias(alias: String, isPublished: Boolean)
    }

    var callback: Callback? = null

    init {
        setData(null)
    }

    override fun buildModels(data: RoomAliasViewState?) {
        data ?: return

        // Published
        buildPublishInfo(data)
        // Local
        buildLocalInfo(data)
    }

    private fun buildPublishInfo(data: RoomAliasViewState) {
        buildProfileSection(
                stringProvider.getString(R.string.room_alias_published_alias_title)
        )
        settingsInfoItem {
            id("publishedInfo")
            helperTextResId(R.string.room_alias_published_alias_subtitle)
        }

        // TODO Set/Unset Canonical

        if (data.alternativeAliases.isEmpty()) {
            settingsInfoItem {
                id("otherPublishedEmpty")
                if (data.actionPermissions.canChangeCanonicalAlias) {
                    helperTextResId(R.string.room_alias_address_empty_can_add)
                } else {
                    helperTextResId(R.string.room_alias_address_empty)
                }
            }
        } else {
            settingsInfoItem {
                id("otherPublished")
                helperTextResId(R.string.room_alias_published_other)
            }
            data.alternativeAliases.forEachIndexed { idx, altAlias ->
                profileActionItem {
                    id("alt_$idx")
                    title(altAlias)
                    listener { callback?.openAlias(altAlias, true) }
                }
            }
        }

        if (data.actionPermissions.canChangeCanonicalAlias) {
            buildPublishManuallyForm(data)
        }
    }

    private fun buildPublishManuallyForm(data: RoomAliasViewState) {
        when (data.publishManuallyState) {
            RoomAliasViewState.AddAliasState.Hidden -> Unit
            RoomAliasViewState.AddAliasState.Closed -> {
                settingsButtonItem {
                    id("publishManually")
                    colorProvider(colorProvider)
                    buttonTitleId(R.string.room_alias_published_alias_add_manually)
                    buttonClickListener { callback?.toggleManualPublishForm() }
                }
            }
            is RoomAliasViewState.AddAliasState.Editing -> {
                formEditTextItem {
                    id("publishManuallyEdit")
                    value(data.publishManuallyState.value)
                    showBottomSeparator(false)
                    hint(stringProvider.getString(R.string.room_alias_address_hint))
                    onTextChange { text ->
                        callback?.setNewAlias(text)
                    }
                }
                settingsContinueCancelItem {
                    id("publishManuallySubmit")
                    continueText(stringProvider.getString(R.string.room_alias_published_alias_add_manually_submit))
                    continueOnClick { callback?.addAlias() }
                    cancelOnClick { callback?.toggleManualPublishForm() }
                }
            }
        }
    }

    private fun buildLocalInfo(data: RoomAliasViewState) {
        buildProfileSection(
                stringProvider.getString(R.string.room_alias_local_address_title)
        )
        settingsInfoItem {
            id("localInfo")
            helperText(stringProvider.getString(R.string.room_alias_local_address_subtitle, data.homeServerName))
        }

        when (val localAliases = data.localAliases) {
            is Uninitialized -> {
                loadingItem {
                    id("loadingAliases")
                }
            }
            is Success -> {
                localAliases().forEachIndexed { idx, localAlias ->
                    profileActionItem {
                        id("loc_$idx")
                        title(localAlias)
                        listener { callback?.openAlias(localAlias, false) }
                    }
                }
            }
            is Fail -> {
                errorWithRetryItem {
                    id("alt_error")
                    text(errorFormatter.toHumanReadable(localAliases.error))
                }
            }
        }

        // Add local
        buildAddLocalAlias(data)
    }

    private fun buildAddLocalAlias(data: RoomAliasViewState) {
        when (data.newLocalAliasState) {
            RoomAliasViewState.AddAliasState.Hidden -> Unit
            RoomAliasViewState.AddAliasState.Closed -> {
                settingsButtonItem {
                    id("newLocalAliasButton")
                    colorProvider(colorProvider)
                    buttonTitleId(R.string.room_alias_local_address_add)
                    buttonClickListener { callback?.toggleLocalAliasForm() }
                }
            }
            is RoomAliasViewState.AddAliasState.Editing -> {
                roomAliasEditItem {
                    id("newLocalAlias")
                    value(data.newLocalAliasState.value)
                    homeServer(":" + data.homeServerName)
                    showBottomSeparator(false)
                    errorMessage(roomAliasErrorFormatter.format((data.newLocalAliasState.asyncRequest as? Fail)?.error as? RoomAliasError))
                    onTextChange { value ->
                        callback?.setNewLocalAliasLocalPart(value)
                    }
                }
                settingsContinueCancelItem {
                    id("newLocalAliasSubmit")
                    continueText(stringProvider.getString(R.string.action_add))
                    continueOnClick { callback?.addLocalAlias() }
                    cancelOnClick { callback?.toggleLocalAliasForm() }
                }
            }
        }
    }
}
