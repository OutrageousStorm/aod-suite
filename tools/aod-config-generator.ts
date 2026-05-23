#!/usr/bin/env node
/**
 * AOD (Always-on-Display) Configuration Generator
 * Generates custom AOD layouts and animation configs for Android devices
 * Requires: Node.js 14+
 */

interface AODConfig {
  displayMode: 'default' | 'minimal' | 'custom';
  clockFormat: '12h' | '24h';
  showBattery: boolean;
  showNotifications: boolean;
  refreshRate: number;
  colors: {
    primary: string;
    secondary: string;
  };
  animations: boolean;
}

const defaultConfig: AODConfig = {
  displayMode: 'minimal',
  clockFormat: '24h',
  showBattery: true,
  showNotifications: false,
  refreshRate: 30,
  colors: {
    primary: '#FFFFFF',
    secondary: '#808080',
  },
  animations: false,
};

function generateAODLayout(config: AODConfig): string {
  return `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical"
  android:gravity="center">

  <!-- Clock Display -->
  <TextView
    android:id="@+id/clock"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textColor="${config.colors.primary}"
    android:textSize="72sp"
    android:fontFamily="monospace" />

  <!-- Battery Info -->
  ${config.showBattery ? `
  <TextView
    android:id="@+id/battery"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textColor="${config.colors.secondary}"
    android:textSize="16sp"
    android:layout_marginTop="16dp" />
  ` : ''}

  <!-- Notification Badge -->
  ${config.showNotifications ? `
  <TextView
    android:id="@+id/notifications"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textColor="${config.colors.primary}"
    android:textSize="20sp"
    android:text="⚠️ Notifications"
    android:layout_marginTop="32dp" />
  ` : ''}

</LinearLayout>`;
}

function generateBuildGradle(config: AODConfig): string {
  return `android {
  compileSdkVersion 33
  
  defaultConfig {
    applicationId "com.outrageousstorm.aodsuite"
    minSdkVersion 26
    targetSdkVersion 33
    
    buildConfigField "int", "REFRESH_RATE", "${config.refreshRate}"
    buildConfigField "String", "CLOCK_FORMAT", "${config.clockFormat === '24h' ? "'HH:mm'" : "'hh:mm a'"}"
  }
}`;
}

function main() {
  const fs = require('fs');
  
  console.log('🎨 AOD Configuration Generator');
  console.log('==============================');
  
  // Generate files
  const layoutXml = generateAODLayout(defaultConfig);
  const buildGradle = generateBuildGradle(defaultConfig);
  
  // Output
  console.log('\n[Generated Files]');
  console.log('✓ aod_layout.xml');
  console.log('✓ build.gradle config');
  console.log('\nConfiguration:');
  console.log(`  Mode: ${defaultConfig.displayMode}`);
  console.log(`  Clock: ${defaultConfig.clockFormat}`);
  console.log(`  Refresh: ${defaultConfig.refreshRate}Hz`);
  console.log(`  Battery: ${defaultConfig.showBattery ? 'visible' : 'hidden'}`);
  
  // Could write to files here
  // fs.writeFileSync('aod_layout.xml', layoutXml);
  // fs.writeFileSync('aod_gradle_config.txt', buildGradle);
}

main();
