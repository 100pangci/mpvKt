package live.mehiz.mpvkt.di

import live.mehiz.mpvkt.ui.custombuttons.CustomButtonsScreenViewModel
import live.mehiz.mpvkt.ui.history.HistoryViewModel
import live.mehiz.mpvkt.ui.network.NetworkViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val ViewModelModule = module {
  viewModelOf(::CustomButtonsScreenViewModel)
  viewModelOf(::HistoryViewModel)
  viewModelOf(::NetworkViewModel)
}
