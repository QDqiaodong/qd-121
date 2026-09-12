<template>
  <div class="app-container">
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon :size="28" color="#fff"><Grid /></el-icon>
          <h1 class="app-title">冲压车间垫板货架层位绑定管理系统</h1>
        </div>
        <div class="header-right">
          <el-tag type="success" size="large">运行正常</el-tag>
        </div>
      </el-header>
      <el-container>
        <el-aside width="220px" class="app-aside">
          <el-menu
            :default-active="activeMenu"
            :router="true"
            background-color="#001529"
            text-color="#ffffffa6"
            active-text-color="#409EFF"
            class="aside-menu"
          >
            <el-menu-item index="/">
              <el-icon><DataAnalysis /></el-icon>
              <span>数据概览</span>
            </el-menu-item>
            <el-menu-item index="/pad">
              <el-icon><Goods /></el-icon>
              <span>垫板档案管理</span>
            </el-menu-item>
            <el-menu-item index="/pad/import">
              <el-icon><UploadFilled /></el-icon>
              <span>垫板批量导入</span>
            </el-menu-item>
            <el-menu-item index="/shelf">
              <el-icon><Box /></el-icon>
              <span>货架层位管理</span>
            </el-menu-item>
            <el-menu-item index="/layer-block">
              <el-icon><Lock /></el-icon>
              <span>层位封锁管理</span>
            </el-menu-item>
            <el-menu-item index="/layer-expand">
              <el-icon><TrendCharts /></el-icon>
              <span>层位临时扩容</span>
            </el-menu-item>
            <el-menu-item index="/borrow">
              <el-icon><Van /></el-icon>
              <span>垫板领用归还</span>
            </el-menu-item>
            <el-menu-item index="/maintenance">
              <el-icon><Tools /></el-icon>
              <span>垫板保养台账</span>
            </el-menu-item>
            <el-menu-item index="/scrap">
              <el-icon><Delete /></el-icon>
              <span>垫板报废出库</span>
            </el-menu-item>
            <el-menu-item index="/record">
              <el-icon><Document /></el-icon>
              <span>调整记录台账</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <el-main class="app-main">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style lang="scss" scoped>
.app-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.app-header {
  background: linear-gradient(90deg, #1e3a8a 0%, #2563eb 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .app-title {
      margin: 0;
      color: #fff;
      font-size: 20px;
      font-weight: 600;
      letter-spacing: 1px;
    }
  }
}

.app-aside {
  background-color: #001529;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);

  .aside-menu {
    border: none;
    height: 100%;

    :deep(.el-menu-item) {
      height: 54px;
      line-height: 54px;
      margin: 4px 0;

      &:hover {
        background-color: #112240;
      }

      &.is-active {
        background-color: #1e3a8a;
      }
    }
  }
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
