import { ref, watch } from 'vue'

/**
 * 关系图的公共偏好。
 *
 * 三个关系图弹窗(标签 / 目录 / 文件)是三个独立组件实例,
 * 用一个模块级的 ref 共享状态, 改一个另外两个也跟着变。
 */

const SPRING_BACK_KEY = 'fm.graph.springBack'

/** 拖动节点松手后是否弹回原位 */
export const springBack = ref(localStorage.getItem(SPRING_BACK_KEY) !== '0')

watch(springBack, (val) => {
  localStorage.setItem(SPRING_BACK_KEY, val ? '1' : '0')
})
