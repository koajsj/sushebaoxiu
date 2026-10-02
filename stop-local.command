#!/bin/bash
project_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$project_root/scripts/local-runtime.sh" stop
status=$?
if [ "$status" -ne 0 ] && [ -t 0 ] && [ "${LOCAL_NO_PAUSE:-0}" != 1 ]; then
  printf '按回车关闭窗口…'
  read -r _
fi
exit "$status"
