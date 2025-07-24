# FaceNet Model File

This directory should contain the FaceNet mobile TensorFlow Lite model file.

## Required Model File:
- **File Name**: `facenet_mobile.tflite`
- **Size**: ~10MB
- **Input**: 160x160x3 RGB image (normalized to [-1, 1])
- **Output**: 512-dimensional face embedding

## How to Obtain the Model:

### Option 1: Pre-trained FaceNet Model
Download a pre-trained FaceNet mobile model optimized for TensorFlow Lite:
- Search for "FaceNet TensorFlow Lite mobile" models
- Ensure input size is 160x160x3
- Ensure output is 512-dimensional embedding

### Option 2: Convert from TensorFlow
If you have a TensorFlow FaceNet model:
```python
import tensorflow as tf

# Load your FaceNet model
model = tf.keras.models.load_model('path_to_facenet_model')

# Convert to TensorFlow Lite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save the model
with open('facenet_mobile.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Option 3: Use Alternative Models
- MobileFaceNet
- ArcFace Mobile
- Other mobile-optimized face recognition models

## Performance Expectations:
- **Inference Time**: ~100ms on mobile CPU
- **Memory Usage**: ~50MB
- **Accuracy**: 90-95% (vs current 70-80%)
- **Feature Size**: 512D (vs current 420D)

## Note:
The TensorFlowFaceRecognition.kt engine is currently implemented with placeholder code.
Once you add the actual model file here, remove the placeholder implementation and enable the real TensorFlow Lite inference.