<template>
  <div class="test-container">
    <h1>测试页面</h1>
    <p>如果您能看到这个页面，说明前端服务正常工作。</p>
    <el-button @click="testAPI">测试API</el-button>
    <div v-if="apiResult" class="api-result">
      <h3>API测试结果:</h3>
      <pre>{{ apiResult }}</pre>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue"
import { getRecycleBinFiles } from "@/api/file"

const apiResult = ref(null)

const testAPI = async () => {
  try {
    const response = await getRecycleBinFiles()
    apiResult.value = response
  } catch (error) {
    apiResult.value = error.message
  }
}
</script>

<style scoped>
.test-container {
  padding: 20px;
  text-align: center;
}

.api-result {
  margin-top: 20px;
  text-align: left;
  background: #f5f5f5;
  padding: 15px;
  border-radius: 5px;
}
</style>
