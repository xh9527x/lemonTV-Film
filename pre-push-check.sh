#!/bin/bash
# lemonTV-Film 推送前本地预检（替代云端构建试错）
# 用法：bash pre-push-check.sh
set -e
cd ~/workspace/lemonTV-Film

AAPT=~/android-sdk/build-tools/35.0.0/aapt2
FAIL=0

echo "=== 1. AAPT2 编译所有资源（抓 XML 命名空间/格式错误）==="
for mod in app-mobile shared; do
  if [ -d "$mod/src/main/res" ]; then
    rm -rf /tmp/aapt-check && mkdir -p /tmp/aapt-check
    if ! $AAPT compile --dir "$mod/src/main/res" -o /tmp/aapt-check/ 2>&1; then
      echo "FAIL: $mod 资源编译失败"; FAIL=1
    else
      echo "OK: $mod"
    fi
  fi
done

echo "=== 2. 字符串/资源名查重（抓重复定义）==="
for f in app-mobile/src/main/res/values/*.xml; do
  dupes=$(grep -o '<string name="[^"]*"' "$f" | sort | uniq -d)
  if [ -n "$dupes" ]; then echo "FAIL: $f 重复: $dupes"; FAIL=1; fi
done
echo "字符串查重完成"

echo "=== 3. Kotlin 括号平衡 ==="
for f in $(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep '\.kt$'); do
  [ -f "$f" ] || continue
  for pair in "{ }" "( )"; do
    set -- $pair
    a=$(grep -o "$1" "$f" | wc -l); b=$(grep -o "$2" "$f" | wc -l)
    if [ "$a" != "$b" ]; then echo "FAIL: $f 括号不平衡 $1$a $2$b"; FAIL=1; fi
  done
done
echo "括号检查完成"

if [ $FAIL -eq 0 ]; then echo "=== 预检通过 ==="; else echo "=== 预检失败 ==="; exit 1; fi
