package cn.walkpaint

/** The same prominent disclosure is shown at first use and when managing map consent. */
internal object AmapDisclosure {
    const val PRIVACY_URL = "https://lbs.amap.com/home/privacy/"
    const val SUMMARY = "高德地图与搜索服务由北京高德图强科技有限公司提供，用于显示地图、地名和查找附近探索点；" +
        "可能收集位置、IP/网络、卫星定位、设备、系统和传感器信息。原始足迹保存在本机。" +
        "同意后地图会联网，可在“我的 → 更多设置 → 隐私与权限”撤回地图授权。"
}
