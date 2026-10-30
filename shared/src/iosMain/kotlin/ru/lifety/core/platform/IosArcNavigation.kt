package ru.lifety.core.platform

import platform.UIKit.UIViewController

data class IosArcItem(val route: String, val title: String, val symbol: String)
interface IosArcNavigationEvents {
    fun onSelect(route: String)
    fun onExpanded(expanded: Boolean)
}
interface IosArcNavigationSession {
    val viewController: UIViewController
    fun update(items: List<IosArcItem>, selected: String, accent: Long, text: Long, dark: Boolean, safeBottom: Double)
    fun close()
}
interface IosArcNavigationFactory { fun create(events: IosArcNavigationEvents): IosArcNavigationSession }
