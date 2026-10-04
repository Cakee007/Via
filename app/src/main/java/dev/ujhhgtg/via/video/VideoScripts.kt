package dev.ujhhgtg.via.video

/** c8.uc: the exact page-side video lookup used by the native fullscreen controller. */
object VideoScripts {
    private const val FIND = "function findVideoElement(){var a=null,b=document.fullscreenElement||document.mozFullScreenElement||document.webkitFullscreenElement||document.msFullscreenElement;b&&(a=\"VIDEO\"===b.tagName?b:firstVideoElement(b));a||(a=firstVideoElement(document));if(!a){b=document.getElementsByTagName(\"iframe\");for(var c=0,d=b.length;c<d&&!(a=b[c].contentDocument||b[c].contentWindow.document,a=firstVideoElement(a));c++);}return a?/game/i.test(a.className+\" \"+a.id)?null:a:null}\nfunction firstVideoElement(a){if(!a)return null;a=a.getElementsByTagName(\"video\");return 0>=a.length?null:a[0]};"
    fun metadata() = "javascript:(function(){__GET_VIDEO_FUNCTION__;var a=findVideoElement();return a?\"VIDEO\"!=a.tagName?\"0,1,16,9\":a.duration+\",\"+a.playbackRate+\",\"+a.videoWidth+\",\"+a.videoHeight:\"\"})();".replace("__GET_VIDEO_FUNCTION__", FIND)
    fun playbackState() = "javascript:(function(){__GET_VIDEO_FUNCTION__;var a=findVideoElement();return a&&\"VIDEO\"==a.tagName?a.paused?1:2:0})();".replace("__GET_VIDEO_FUNCTION__", FIND)
    fun seekBy(seconds: Int) = "javascript:(function(){__GET_VIDEO_FUNCTION__;var b=findVideoElement();if(b)try{var a=${seconds}+b.currentTime,c=b.duration;0>a?a=0:a>c&&(a=c);b.currentTime=a}catch(d){}})();".replace("__GET_VIDEO_FUNCTION__", FIND)
    fun togglePlayback() = "javascript:(function(){__GET_VIDEO_FUNCTION__;var a=findVideoElement();if(a)try{var b=!a.paused;b?a.pause():a.play();return b?1:2}catch(c){}return 0})();".replace("__GET_VIDEO_FUNCTION__", FIND)
    fun setPlaybackRate(rate: Float) = "javascript:(function(){__GET_VIDEO_FUNCTION__;var a=findVideoElement();if(a)try{a.playbackRate=${rate}}catch(b){}})();".replace("__GET_VIDEO_FUNCTION__", FIND)
}
