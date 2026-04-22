import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep3Component } from './claim-step3.component';

describe('ClaimStep3Component', () => {
  let component: ClaimStep3Component;
  let fixture: ComponentFixture<ClaimStep3Component>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep3Component]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep3Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
