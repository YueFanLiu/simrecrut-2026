import { createPinia } from 'pinia'
import { demoMode } from '../config/runtime.js'
import { installDemoCheckpoints } from './demo-checkpoints.js'

export const pinia = createPinia()
// Restore simulated checkpoints only in the explicit development demo.
if (demoMode) pinia.use(installDemoCheckpoints)
