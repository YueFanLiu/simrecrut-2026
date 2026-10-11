import { createPinia } from 'pinia'
import { installDemoCheckpoints } from './demo-checkpoints.js'

export const pinia = createPinia()
pinia.use(installDemoCheckpoints)
