import numpy as np
import torch
import torch.nn.functional as F


def iou_score(output, target):
    smooth = 1e-5

    if torch.is_tensor(output):
        output = torch.sigmoid(output).data.cpu().numpy()
    if torch.is_tensor(target):
        target = target.data.cpu().numpy()
    output_ = output > 0.5
    target_ = target > 0.5
    # print(np.sum(target>0.5))
    intersection = (output_ & target_).sum()
    union = (output_ | target_).sum()

    return (intersection + smooth) / (union + smooth)


def dice_coef(output, target):
    smooth = 1e-5

    output = torch.sigmoid(output).view(-1).data.cpu().numpy()
    target = target.view(-1).data.cpu().numpy()
    intersection = (output * target).sum()

    return (2. * intersection + smooth) / \
        (output.sum() + target.sum() + smooth)

def pixel_acc(output, target):
    smooth = 1e-5

    output = torch.sigmoid(output).view(-1).data.cpu().numpy()
    target = target.view(-1).data.cpu().numpy()
    
    output_ = np.array(output > 0.5)
    target_ = np.array(target > 0.5)
    
    FP = np.float(np.sum((output_==True) & (target_==False)))
    FN = np.float(np.sum((output_==False) & (target_==True)))
    TP = np.float(np.sum((output_==True) & (target_==True)))
    TN = np.float(np.sum((output_==False) & (target_==False)))
    
    N = FP + FN + TP + TN
    accuracy = np.divide(TP + TN, N)

    return accuracy