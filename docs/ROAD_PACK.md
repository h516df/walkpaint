# 本地道路包（v0.5）

在地图顶部点「导入真实道路包」，或「我的 → 导入 / 查看道路包」，通过 Android 系统文件选择器选文件。校验失败保留全部原足迹和道路；成功后匹配历史足迹，青绿色只绘制已确认区间，灰色绘制未涂道路和细 GPS 参考。道路包覆盖范围之外不涂色。

默认地图完全离线，无网络来源、瓦片、字体或道路匹配请求。「我的 → 联网底图 / 离线道路」会单独说明浏览区域/IP 的发送，只有主动选择“同意联网并记住”才加载 OpenFreeMap；选择保存在本机，今后打开地图也会联网。可在同一入口选择“使用离线道路”，离线选择同样持久保存。高德仍需有效 Key 和已有独立 SDK 隐私授权；本轮没有 Key，因此没有验收。

## 格式和合法来源

道路只能来自有权使用的 **完整 OSM way**，经纬度为 WGS84 `[longitude, latitude]`。不能导入公共托管瓦片的裁剪几何、瓦片 ID、GCJ-02、匿名直线或第三方未授权道路。v0.6 提供可选的附近下载：单次明确同意后向 https://overpass-api.de/api/interpreter POST 一个中心约100米取整、约1.2公里边长的区域，返回完整 way 的版本和几何。采用 `out meta geom;`，不加几何输出裁剪范围；不保存编辑者信息，不发送完整轨迹或历史路线。不自动更新、重试或改用其他主机。服务不可用、区域太密或 OSM 缺路时失败提示并保留原数据；仍支持已有合法文件。

本仓库公开地点测试包：[oxford-roads.geojson](../app/src/test/resources/oxford-roads.geojson)。它是 **真实牛津道路几何**，不是手绘模拟路。2026-10-09 从 OSM 官方小范围 map 数据接口读取完整 way 与 nodes，确认全部节点齐全，再转为 GeoJSON；道路 ID 为 1675518、1675892、1675893、1675894、4884545、895264517。原始接口：<https://api.openstreetmap.org/api/0.6/map.json?bbox=-1.256,51.753,-1.254,51.755>。这是固定公共测试区域，与用户位置无关；**不应在 OSM 编辑 API 批量下载城市**。较大区域应使用已经合法取得的 Geofabrik/OSM extracts，本 App 不提供在线下载器。

© OpenStreetMap contributors。数据许可：[ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/)，署名与使用条款见 [OSM copyright](https://www.openstreetmap.org/copyright)。测试包是 OSM 数据的转换，按 ODbL 提供；定位轨迹仍属私人数据，不自动分享。将来若对外分发衍生道路数据库，应按该许可履行署名和适用的同许可义务。

```json
{
  "type": "FeatureCollection",
  "walkpaint": {
    "version": 1,
    "coordinates": "WGS84",
    "license": "ODbL-1.0",
    "attribution": "© OpenStreetMap contributors",
    "name": "道路包名称",
    "source": "https://合法数据来源"
  },
  "features": [{
    "type": "Feature",
    "properties": {"osm_way_id": 1675518, "osm_version": 真实版本整数, "highway": "service"},
    "geometry": {"type": "LineString", "coordinates": [完整真实坐标]}
  }]
}
```

上面展示字段结构，含说明性占位符；可导入的完整文件见测试包。普通 OSM GeoJSON 应先由数据提供者/开发者补齐声明、完整 way 及真实版本，不应让用户猜 ID 或许可。导入器校验声明、整数 ID、道路类别、几何与尺寸，**不能离线证明来源声明真实**，文件提供者仍须保证其使用权和完整性。

每包上限 8MB、2000 way、10万顶点；累计上限5000 way、20万顶点及50万索引单元关联。相邻顶点间隔0.01米至2公里，过大/异常包拒绝并回滚。这是一个小区域的可运行切片，不是全球道路库。

## 匹配、累计和数据保护

- 使用 OSM way ID + 完整、方向规范化几何 SHA256 作持久身份。重复/反向几何导入不产生新道路；同 way 几何发生变化则替换该 way 并从原始足迹重新计算，不复用旧距离轴。闭合道路也有规范方向。
- 定位精度需在1–20米；同 trip、同 segment、时间递增且间隔≤45秒；每对沿路距离3–120米、速度≤8米/秒，限制路程/直线距离比。匹配半径8–20米；不同路或同路自交投影误差差距<4米时拒绝。
- 沿候选真实曲线每≤5米检查附近道路，含内部交叉口；不跨道路猜路由。短掉点、路口、平行路、缺失路段可能造成**少涂**，不是完成全量道路匹配的保证。仅采到两端 GPS 无法证明中间真实走法；本算法使用保守约束，仍需户外真机检验误匹配。
- 只有已经接受的真实定位回调参与自动匹配；演示 trip 不参与道路覆盖。正常覆盖与定位点同一 SQLite transaction 落盘；道路操作有独立 SAVEPOINT，失败完整回滚该点的道路匹配/覆盖，保留已接受 GPS，且不阻止后续 GPS。真实定位表写入或事务恢复失败仍报错；界面说明道路异常，重新导入有效包修复并回算原始足迹。重复/反向/隔天重走取区间并集，未经过区间保留空白。
- SQLite schema1→2 只增加 roads、coverage、road_matches 表，不更改/删除 trips、points。包名、原 debug 证书不变，versionCode6，覆盖升级。
- 手动及本地每日 JSON 备份包含原足迹与完整道路包；导入兼容旧版轨迹 JSON，合并后重新生成覆盖。导入任何备份都不删除已有足迹。路包变化可能改变已确认覆盖，这是重新匹配后的真实结果。
- APK 回退应使用保留的源代码/旧 APK 作为参考。**旧 schema1 App 无法直接打开 schema2，不应降级、卸载或清空数据回退**；若需回退，先另行设计并验收安全兼容方案。保留所有旧 trips/points 不等于旧 APK 支持新 schema。

模拟器 GPS 使用公开道路上的模拟位置，仅验证运行链路，不是用户实走。验收脚本只能操作显式允许的模拟器；不卸载、不清数据、不操作用户手机，不对外导出私人轨迹。

下载格式依据 [Overpass完整几何说明](https://dev.overpass-api.de/overpass-doc/en/full_data/bbox.html)、[数据格式/版本信息](https://dev.overpass-api.de/overpass-doc/en/targets/formats.html) 和 [公共服务使用边界](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html)，用于小区域个人手动请求，不用于批量城市抓取。当前默认来源只有上述主机。
