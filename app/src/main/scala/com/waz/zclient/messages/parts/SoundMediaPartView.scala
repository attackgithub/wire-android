/**
  * Wire
  * Copyright (C) 2017 Wire Swiss GmbH
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  */
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.{Color, PorterDuff}
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.{ImageView, RelativeLayout, TextView}
import com.waz.api.Message
import com.waz.model.MessageContent
import com.waz.model.messages.media.{ArtistData, MediaAssetData}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.State
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.utils._
import com.waz.zclient.utils._
import com.waz.zclient.controllers.BrowserController
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.ZLog.ImplicitTag._

class SoundMediaPartView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ClickableViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val imageView: ImageView = findById(R.id.iv__image)
  lazy val titleView: TextView = findById(R.id.ttv__title)
  lazy val artistView: TextView = findById(R.id.ttv__artist)
  lazy val playView: GlyphTextView = findById(R.id.gtv__play)
  lazy val errorView: GlyphTextView = findById(R.id.gtv__error)
  lazy val mediaNameView: TextView = findById(R.id.ttv__medianame)
  lazy val iconView: ImageView = findById(R.id.iv__icon)

  override val tpe = MsgPart.SoundMedia

  inflate(R.layout.message_soundmedia_content)

  private val content = Signal[MessageContent]()
  private val media = content map { c => c.richMedia match {
    case None => MediaAssetData.empty(c.tpe)
    case Some(richMedia) => richMedia
  }}

  private val image = media.flatMap {
    _.artwork.fold2(Signal.empty[ImageSource], id => Signal.const[ImageSource](WireImage(id)))
  }

  private val imageDrawable = new ImageAssetDrawable(
    image,
    background = Some(new ColorDrawable(getColor(R.color.content__youtube__background_color)))
  )

  imageDrawable.setColorFilter(
    ColorUtils.injectAlpha(getResourceFloat(R.dimen.content__youtube__alpha_overlay), Color.BLACK),
    PorterDuff.Mode.DARKEN
  )

  imageView.setBackground(imageDrawable)
  imageView.setVisible(true)
  playView.setVisible(true)
  playView.bringToFront()
  errorView.setVisible(false)

  val loadingFailed = imageDrawable.state.map {
    case State.Failed(_, _) => true
    case _ => false
  }

  loadingFailed { failed =>
    playView.setVisible(!failed)
    errorView.setVisible(failed)
  }

  loadingFailed.flatMap {
    case false =>
      content.map(_.tpe).map {
        case Message.Part.Type.SPOTIFY    => (R.string.mediaplayer__message__onspotify, R.drawable.spotify)
        case Message.Part.Type.SOUNDCLOUD => (R.string.mediaplayer__message__onsoundcloud, R.drawable.soundcloud)
        case _ => (-1, -1)
      }
    case true => Signal((-1, -1))
  }.map {
    case (nameId, iconId) => ( if(nameId == -1) "" else getString(nameId) , if(iconId == -1) null else getDrawable(iconId) )
  }{
    case (name, icon) =>
      mediaNameView.setText(name)
      iconView.setBackground(icon)
  }

  loadingFailed.flatMap {
    case false =>
      media.map(m => (m.title, m.artist)).map {
        case (title, None) => (title, "")
        case (title, Some(ArtistData(artistName, _))) => (title, artistName)
      }
    case true => Signal(("", ""))
  }{
    case (title, artistName) =>
      titleView.setText(title)
      artistView.setText(artistName)
  }

  override def set(msg: MessageAndLikes, part: Option[MessageContent], opts: MsgBindOptions): Unit = {
    super.set(msg, part, opts)
    part foreach {
      content ! _
    }
  }

  val browser = inject[BrowserController]

  onClicked { _ => content.currentValue.map(_.contentAsUri).foreach(browser.openUrl) }

}
