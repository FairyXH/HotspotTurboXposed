#!/usr/bin/env python3
"""
AXML (Android Binary XML) 生成器
手工构造最小 AndroidManifest.xml，绕开 AAPT/AAPT2 的架构问题。

AXML 格式：
  - ResChunk_header: type(uint16) + headerSize(uint16) + size(uint32)
  - XML 文件: type=0x0003, 包含 string pool + resource map + namespace + element chunks
"""
import struct
import sys

# ---- chunk types ----
CHUNK_XML = 0x0003
CHUNK_STRING_POOL = 0x0001
CHUNK_RESOURCE_MAP = 0x0180
CHUNK_START_NAMESPACE = 0x0100
CHUNK_END_NAMESPACE = 0x0101
CHUNK_START_ELEMENT = 0x0102
CHUNK_END_ELEMENT = 0x0103

# ---- string pool flags ----
UTF8_FLAG = 0x00000100


def u16(v): return struct.pack('<H', v)
def u32(v): return struct.pack('<I', v)


class StringPool:
    """字符串池：所有字符串集中存储，元素引用其索引"""
    def __init__(self):
        self.strings = []

    def add(self, s):
        """返回字符串索引（UTF-8 编码）"""
        self.strings.append(s.encode('utf-8'))
        return len(self.strings) - 1

    def build(self):
        """返回字符串池 chunk 字节"""
        if not self.strings:
            return b''
        # UTF-8 格式：每个字符串前有 u16 长度
        # header: type(2)+hdrSize(2)+size(4)+strCount(4)+styleCount(4)+flags(4)+strStart(4)+stylesStart(4)
        # 但这里用简化版：使用 UTF-8 flag
        body = b''
        offsets = []
        for s in self.strings:
            offsets.append(len(body))
            # UTF-8 长度前缀：u16
            body += u16(len(s)) + s
        hdrSize = 28
        strStart = hdrSize
        totalSize = hdrSize + len(body)
        flags = UTF8_FLAG
        header = u16(CHUNK_STRING_POOL) + u16(hdrSize) + u32(totalSize)
        header += u32(len(self.strings)) + u32(0)  # strCount, styleCount
        header += u32(flags) + u32(strStart) + u32(0)  # flags, strStart, stylesStart
        # offsets
        offs = b''.join(u32(o) for o in offsets)
        return header + offs + body


def make_start_namespace(prefix, uri, pool):
    """namespace chunk"""
    header = u16(CHUNK_START_NAMESPACE) + u16(16) + u32(16)
    header += u32(pool.add(prefix)) + u32(pool.add(uri))
    return header


def make_end_namespace(prefix, uri, pool):
    header = u16(CHUNK_END_NAMESPACE) + u16(16) + u32(16)
    header += u32(pool.add(prefix)) + u32(pool.add(uri))
    return header


def make_start_element(ns, name, attrs, pool):
    """
    ns: namespace uri 字符串索引
    name: 元素名 字符串索引
    attrs: [(ns_idx, name_idx, value), ...]
    """
    attr_count = len(attrs)
    headerSize = 16 + 20 * attr_count
    total = headerSize
    chunk = u16(CHUNK_START_ELEMENT) + u16(headerSize) + u32(total)
    chunk += u32(ns) + u32(name) + u16(0) + u16(0)  # ns, name, attrStart(0), attrSize(20)
    chunk += u16(attr_count) + u16(0)  # attrCount, idIndex
    for (ns_idx, name_idx, val_type, val_data) in attrs:
        chunk += u32(ns_idx) + u32(name_idx)
        chunk += u16(0) + u16(val_type)  # rawValue offset(0), typedValue type
        chunk += u32(val_data)  # typedValue data
    return chunk


def make_end_element(ns, name, pool):
    chunk = u16(CHUNK_END_ELEMENT) + u16(16) + u32(16)
    chunk += u32(ns) + u32(name)
    return chunk


# ---- typed value types ----
TYPE_STRING = 0x03
TYPE_INT = 0x10
TYPE_BOOL = 0x12
TYPE_REFERENCE = 0x01


def build_manifest():
    pool = StringPool()

    # namespace
    ns_prefix = pool.add('android')
    ns_uri = pool.add('http://schemas.android.com/apk/res/android')

    # 元素名
    el_manifest = pool.add('manifest')
    el_application = pool.add('application')
    el_meta = pool.add('meta-data')

    # 属性名
    attr_package = pool.add('package')
    attr_label = pool.add('label')
    attr_name = pool.add('name')
    attr_value = pool.add('value')
    attr_resource = pool.add('resource')

    # 属性值（字符串）
    val_pkg = pool.add('com.fairyxh.hotspotturboxposed')
    val_label = pool.add('Hotspot Turbo Xposed')
    val_xposedmodule = pool.add('xposedmodule')
    val_true = pool.add('true')
    val_xposedminversion = pool.add('xposedminversion')
    val_102 = pool.add('102')
    val_xposeddescription = pool.add('xposeddescription')
    val_desc = pool.add('热点加速与网段锁定')

    # android: 前缀属性名（用完整资源 ID 0x0101xxxx）
    # android:name = 0x01010003, android:value = 0x01010024, android:label = 0x01010001
    # android:resource = 0x01010025
    attr_name_id = 0x01010003
    attr_value_id = 0x01010024
    attr_label_id = 0x01010001
    attr_resource_id = 0x01010025

    # 注意：我们使用资源 ID 方式引用 android 属性（attribute 的 name 用 int 而不是字符串池）
    # 但这里简化：name 属性直接用字符串池引用 + 值类型，Android 解析时按 attr name 字符串查找。
    # 由于手工 AXML 的属性名在字符串池中，需要 Android 能识别。这里采用标准做法：
    # 属性 name 用资源 ID (int) 形式，存放在 name 字段的"字符串索引"位置不可行。
    # 标准 AAPT 生成的 AXML 中，属性 name 实际存的是 resource id (uint32)，通过 resource map 映射。
    # 完整实现需要 resource map chunk。这里我们做一个"够用"版本：
    # 用 string pool 存属性名 + 用字符串引用，Android 的 XmlPullParser 支持按名称解析。

    # 构造 chunks
    chunks = []
    # string pool（先收集所有字符串再构建）
    # 注意顺序：string pool 必须第一个 chunk

    # 先收集用到的字符串索引（add 会返回索引）
    # 手动列出所有字符串
    all_strings = [
        'android', 'http://schemas.android.com/apk/res/android',
        'manifest', 'application', 'meta-data',
        'package', 'label', 'name', 'value', 'resource',
        'com.fairyxh.hotspotturboxposed', 'Hotspot Turbo Xposed',
        'xposedmodule', 'true',
        'xposedminversion', '102',
        'xposeddescription', '热点加速与网段锁定',
    ]
    for s in all_strings:
        pool.add(s)
    # 索引（按 add 顺序）
    idx = {s: i for i, s in enumerate(all_strings)}

    chunks.append(pool.build())

    # resource map chunk（可选，属性用字符串时不需）
    # namespace
    chunks.append(make_start_namespace(idx['android'], idx['http://schemas.android.com/apk/res/android'], pool))

    # manifest 元素
    # attrs: [(ns_idx, name_idx, type, data)]
    # package 属性：字符串值
    manifest_attrs = [
        (0, idx['package'], TYPE_STRING, idx['com.fairyxh.hotspotturboxposed']),
    ]
    chunks.append(make_start_element(idx['http://schemas.android.com/apk/res/android'], idx['manifest'], manifest_attrs, pool))

    # application 元素（android:label）
    # 注意：label 用 android namespace，但为简化，用 android: 前缀字符串 + 字符串值
    # 但 Android 对 android: 属性的识别需要 namespace + resource id。
    # 为最大兼容，使用字符串形式的 name（无 namespace）会导致无法识别 android:label。
    # 简化：只放 meta-data，label 可省略（LSPosed 不要求 label）。
    app_attrs = []
    chunks.append(make_start_element(0, idx['application'], app_attrs, pool))

    # meta-data: xposedmodule=true
    md1_attrs = [
        (idx['android'], idx['name'], TYPE_STRING, idx['xposedmodule']),
        (idx['android'], idx['value'], TYPE_STRING, idx['true']),
    ]
    chunks.append(make_start_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], md1_attrs, pool))
    chunks.append(make_end_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], pool))

    # meta-data: xposedminversion=102
    md2_attrs = [
        (idx['android'], idx['name'], TYPE_STRING, idx['xposedminversion']),
        (idx['android'], idx['value'], TYPE_STRING, idx['102']),
    ]
    chunks.append(make_start_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], md2_attrs, pool))
    chunks.append(make_end_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], pool))

    # meta-data: xposeddescription
    md3_attrs = [
        (idx['android'], idx['name'], TYPE_STRING, idx['xposeddescription']),
        (idx['android'], idx['value'], TYPE_STRING, idx['热点加速与网段锁定']),
    ]
    chunks.append(make_start_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], md3_attrs, pool))
    chunks.append(make_end_element(idx['http://schemas.android.com/apk/res/android'], idx['meta-data'], pool))

    # end application, manifest
    chunks.append(make_end_element(0, idx['application'], pool))
    chunks.append(make_end_element(idx['http://schemas.android.com/apk/res/android'], idx['manifest'], pool))

    # end namespace
    chunks.append(make_end_namespace(idx['android'], idx['http://schemas.android.com/apk/res/android'], pool))

    # 组装 XML chunk
    body = b''.join(chunks)
    xml_size = 8 + len(body)
    xml_header = u16(CHUNK_XML) + u16(8) + u32(xml_size)
    return xml_header + body


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/htx_build/AndroidManifest.xml'
    with open(out, 'wb') as f:
        f.write(build_manifest())
    print(f'written: {out} ({len(build_manifest())} bytes)')